package no.nav.aap.api.søknad.mellomlagring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.spring.pubsub.core.subscriber.PubSubSubscriberTemplate
import com.google.cloud.storage.NotificationInfo.EventType.OBJECT_DELETE
import com.google.cloud.storage.NotificationInfo.EventType.OBJECT_FINALIZE
import com.google.cloud.storage.NotificationInfo.EventType.valueOf
import com.google.pubsub.v1.PubsubMessage
import java.util.*
import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.felles.SkjemaType
import no.nav.aap.api.søknad.mellomlagring.BucketConfig.Companion.SKJEMATYPE
import no.nav.aap.api.søknad.mellomlagring.BucketConfig.Companion.UUID_
import no.nav.aap.api.søknad.mellomlagring.MellomlagringEventSubscriber.Metadata.Companion.getInstance
import no.nav.aap.api.søknad.minside.MinSideClient
import no.nav.aap.api.søknad.minside.MinSideNotifikasjonType.Companion.SØKNADSTD
import no.nav.aap.util.LoggerUtil.getLogger
import no.nav.aap.util.MDCUtil.NAV_CALL_ID
import no.nav.aap.util.MDCUtil.toMDC
import no.nav.boot.conditionals.ConditionalOnGCP
import no.nav.boot.conditionals.EnvUtil.CONFIDENTIAL
import org.springframework.boot.CommandLineRunner

@Suppress("BlockingMethodInNonBlockingContext")
@ConditionalOnGCP
class MellomlagringEventSubscriber(private val dittNav: MinSideClient,
                                   private val cfg: BucketConfig,
                                   private val mapper: ObjectMapper,
                                   private val subscriber: PubSubSubscriberTemplate) : CommandLineRunner{

    private val log = getLogger(javaClass)

    override fun run(vararg args: String?) {
        subscribe()
    }
    private fun subscribe() =
        with(cfg.mellom) {
            log.trace("Abonnererer på hendelser i $subscription")
            subscriber.subscribe(subscription.navn) { event ->
                event.ack()
                with(event.pubsubMessage) {
                    val type = eventType()
                    log.trace(CONFIDENTIAL, "Data i $type event er ${data.toStringUtf8()}, attributter er $attributesMap")
                    when (type) {
                        OBJECT_FINALIZE -> opprettet(metadata())
                        OBJECT_DELETE -> slettet(metadata())
                        else -> log.warn("Event $type ikke håndtert (dette skal aldri skje)")
                    }
                }
            }
        }

    private fun opprettet(metadata: Metadata?) =
        metadata?.let {
            with(it) {
                log.trace(CONFIDENTIAL, "Oppretter beskjed fra metadata $it")
                dittNav.opprettBeskjed(fnr, "Du har en påbegynt ${type.tittel}", eventId, SØKNADSTD, false)
            }
        } ?: log.warn("Fant ikke forventede metadata")

    private fun slettet(metadata: Metadata?) =
        metadata?.let {
            with(it) {
                log.trace(CONFIDENTIAL, "Sletter beskjed fra metadata $it")
                dittNav.avsluttBeskjed(type, fnr, eventId)
            }
        } ?: log.warn("Fant ikke forventede metadata")

    private fun PubsubMessage.data() = mapper.readValue<Map<String, Any>>(data.toStringUtf8())
    private fun PubsubMessage.objektNavn() = attributesMap[OBJECTID]?.split("/")
    private fun PubsubMessage.eventType() =
        attributesMap[EVENT_TYPE]?.let { valueOf(it) }

    private fun PubsubMessage.metadata() =
        with(objektNavn()) {
            if (this?.size == 2) {
                data()[METADATA]?.let {
                    it as Map<String, String>
                    getInstance(it[SKJEMATYPE], this[0], it[UUID_])
                }
            }
            else null
        }

    private data class Metadata private constructor(val type: SkjemaType, val fnr: Fødselsnummer, val eventId: UUID) {
        companion object {
            fun getInstance(type: String?, fnr: String?, eventId: String?) =
                if (eventId != null && fnr != null && type != null) {
                    toMDC(NAV_CALL_ID, eventId)
                    Metadata(SkjemaType.valueOf(type), Fødselsnummer(fnr), UUID.fromString(eventId))
                }
                else {
                    null
                }
        }
    }

    companion object {
        private const val EVENT_TYPE = "eventType"
        private const val METADATA = "metadata"
        private const val OBJECTID = "objectId"
    }


}