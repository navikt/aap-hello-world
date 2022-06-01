package no.nav.aap.api.søknad.dittnav

import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.felles.SkjemaType
import no.nav.aap.api.søknad.dittnav.DittNavConfig.TopicConfig
import no.nav.aap.util.LoggerUtil
import no.nav.boot.conditionals.ConditionalOnGCP
import no.nav.brukernotifikasjon.schemas.builders.BeskjedInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.concurrent.ListenableFutureCallback
import org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequestUri
import java.time.LocalDateTime.now
import java.time.ZoneOffset.UTC
import java.util.*

@Service
@ConditionalOnGCP
class DittNavRouter(private val dittNav: KafkaOperations<NokkelInput, Any>,
                    private val cfg: DittNavConfig,
                    @Value("\${nais.app.name}") private val app: String,
                    @Value("\${nais.namespace}") private val namespace: String,
                    private val meldingRepo: JPADittNavMeldingRepository) {

    private val log = LoggerUtil.getLogger(javaClass)

    @Transactional
    fun opprettBeskjed(fnr: Fødselsnummer, type: SkjemaType) = send(fnr, cfg.beskjed, type)

    private fun send(fnr: Fødselsnummer, cfg: TopicConfig, type: SkjemaType) =
        if (cfg.enabled) {
            with(keyFra(fnr, type.name)) {
                log.info("Sender til Ditt Nav $this")
                dittNav.send(ProducerRecord(cfg.topic, this, beskjed(cfg, type)))
                    .addCallback(DittNavCallback(this, meldingRepo))
            }
        }
        else {
            log.info("Sender ikke melding til Ditt Nav")
        }

    private fun beskjed(cfg: TopicConfig, type: SkjemaType) =
        BeskjedInputBuilder()
            .withSikkerhetsnivaa(3)
            .withTidspunkt(now(UTC))
            .withSynligFremTil(now(UTC).plus(cfg.varighet))
            .withLink(replaceWith("/aap/${type.name}"))
            .withTekst(type.tittel + " mottatt")
            /* .withEksternVarsling(true)
             .withEpostVarslingstekst("AAP-søknad mottat")
             .withEpostVarslingstittel("Tusen takk")
             .withPrefererteKanaler()
             .withSmsVarslingstekst("SMS fra NAV") */
            .build()

    private fun replaceWith(replacement: String) =
        fromCurrentRequestUri().replacePath(replacement).build().toUri().toURL()

    private fun keyFra(fnr: Fødselsnummer, grupperingId: String) =
        NokkelInputBuilder()
            .withFodselsnummer(fnr.fnr)
            .withEventId("${UUID.randomUUID()}")
            .withGrupperingsId(grupperingId)
            .withAppnavn(app)
            .withNamespace(namespace)
            .build()

    private class DittNavCallback(private val key: NokkelInput, private val meldingRepo: JPADittNavMeldingRepository) :
        ListenableFutureCallback<SendResult<NokkelInput, Any>?> {
        private val log = LoggerUtil.getLogger(javaClass)
        override fun onSuccess(result: SendResult<NokkelInput, Any>?) {
            log.info(" Sendte beskjed til Ditt Nav  med id ${key.getEventId()} og offset ${result?.recordMetadata?.offset()} på ${result?.recordMetadata?.topic()}")
            val m = meldingRepo.save(JPADittNavMelding(fnr = key.getFodselsnummer(), ref = key.getEventId()))
            log.info("Lagret info om oversendelse til Ditt Nav med id ${m.id}")
        }

        override fun onFailure(e: Throwable) {
            log.warn("Kunne ikke sende beskjed til Ditt Nav med id ${key.getEventId()}", e)
        }
    }
}