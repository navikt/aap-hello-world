package no.nav.aap.api.søknad.mellomlagring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.spring.pubsub.core.subscriber.PubSubSubscriberTemplate
import com.google.cloud.storage.NotificationInfo.EventType.OBJECT_DELETE
import com.google.cloud.storage.NotificationInfo.EventType.OBJECT_FINALIZE
import com.google.cloud.storage.NotificationInfo.EventType.valueOf
import com.google.pubsub.v1.PubsubMessage
import io.micrometer.core.instrument.Metrics.*
import java.util.*
import java.util.UUID.*
import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.felles.SkjemaType
import no.nav.aap.api.felles.SkjemaType.*
import no.nav.aap.api.søknad.mellomlagring.BucketConfig.Companion.SKJEMATYPE
import no.nav.aap.api.søknad.mellomlagring.BucketConfig.Companion.UUID_
import no.nav.aap.api.søknad.mellomlagring.MellomlagringEventSubscriber.Metadata.Companion.getInstance
import no.nav.aap.api.søknad.minside.MinSideClient
import no.nav.aap.util.LoggerUtil.getLogger
import no.nav.aap.util.MDCUtil.NAV_CALL_ID
import no.nav.aap.util.MDCUtil.toMDC
import no.nav.boot.conditionals.ConditionalOnDev
import no.nav.boot.conditionals.EnvUtil.CONFIDENTIAL
import org.springframework.boot.CommandLineRunner

@Suppress("BlockingMethodInNonBlockingContext")
@ConditionalOnDev
class MellomlagringEventSubscriber(private val dittNav: MinSideClient,
                                   private val cfg: BucketConfig,
                                   private val mapper: ObjectMapper,
                                   private val subscriber: PubSubSubscriberTemplate) : CommandLineRunner {

    private val log = getLogger(javaClass)

    override fun run(vararg args: String?) {
        with(cfg.mellom) {
            log.trace("IAC Abonnererer på hendelser i $subscription")
            subscriber.subscribe(subscription.navn) { event ->
                event.ack()
                with(event.pubsubMessage) {
                    val type = eventType()
                    log.trace(CONFIDENTIAL, "Data i $type er ${data.toStringUtf8()}, attributter er $attributesMap")
                    metadata()?.let {
                        log.info("PubSub event $type med metadata $it")
                        when (type) {
                            OBJECT_FINALIZE -> opprettet(it)
                            OBJECT_DELETE -> avsluttet(it, endeligSlettet())
                            else -> log.warn("Event $type ikke håndtert (dette skal aldri skje)")
                        }
                    } ?: log.warn("Fant ikke forventede metadata i event ${event.pubsubMessage}")
                }
            }
        }
    }

    private fun opprettet(metadata: Metadata) =
        with(metadata) {
            dittNav.opprettUtkast(fnr,"Du har en påbegynt søknad om AAP",eventId).also {}
        }

    private fun avsluttet(metadata: Metadata, endeligSlettet: Boolean) =
        with(metadata) {
            if (endeligSlettet) {
                log.trace("Utkast slettes for $fnr")
                dittNav.avsluttUtkast(fnr)
            }
        }

    private fun PubsubMessage.metadata() =
        with(objektNavn()) {
            if (this?.size == 2) {
                data(mapper)[METADATA]?.let {
                    it as Map<String, String>
                    getInstance(it[SKJEMATYPE], this[0], it[UUID_])
                }
            }
            else {
                null
            }
        }

    private data class Metadata private constructor(val type: SkjemaType, val fnr: Fødselsnummer, val eventId: UUID) {
        companion object {
            fun getInstance(type: String?, fnr: String?, eventId: String?) =
                if (!(eventId == null || fnr == null || type == null)) {
                    toMDC(NAV_CALL_ID, eventId)
                    Metadata(SkjemaType.valueOf(type), Fødselsnummer(fnr), fromString(eventId))
                }
                else {
                    null
                }
        }
    }

    companion object {
        private fun PubsubMessage.data(mapper: ObjectMapper) = mapper.readValue<Map<String, Any>>(data.toStringUtf8())
        private fun PubsubMessage.objektNavn() = attributesMap[OBJECTID]?.split("/")
        private fun PubsubMessage.endeligSlettet() = attributesMap[OVERWRITTEN] == null
        private fun PubsubMessage.eventType() = attributesMap[EVENT_TYPE]?.let { valueOf(it) }
        private const val OVERWRITTEN = "overwrittenByGeneration"
        private const val EVENT_TYPE = "eventType"
        private const val METADATA = "metadata"
        private const val OBJECTID = "objectId"
    }

}