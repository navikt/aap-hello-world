package no.nav.aap.api.søknad.minside

import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.felles.SkjemaType
import no.nav.aap.api.felles.SkjemaType.STANDARD
import no.nav.aap.api.felles.SkjemaType.UTLAND
import no.nav.aap.api.søknad.SendCallback
import no.nav.aap.api.søknad.minside.MinSideBeskjedRepository.Beskjed
import no.nav.aap.api.søknad.minside.MinSideConfig.BacklinksConfig
import no.nav.aap.api.søknad.minside.MinSideNotifikasjonType.MinSideBacklinkContext.MINAAP
import no.nav.aap.api.søknad.minside.MinSideNotifikasjonType.MinSideBacklinkContext.SØKNAD
import no.nav.aap.api.søknad.minside.MinSideOppgaveRepository.Oppgave
import no.nav.aap.util.LoggerUtil.getLogger
import no.nav.boot.conditionals.ConditionalOnGCP
import no.nav.boot.conditionals.EnvUtil.CONFIDENTIAL
import no.nav.brukernotifikasjon.schemas.builders.BeskjedInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.OppgaveInputBuilder
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.core.env.Environment
import org.springframework.kafka.core.KafkaOperations
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.UriComponentsBuilder.fromUri
import java.time.LocalDateTime.now
import java.time.ZoneOffset.UTC
import java.util.*

@ConditionalOnGCP
class MinSideClient(private val dittNav: KafkaOperations<NokkelInput, Any>,
                    private val cfg: MinSideConfig,
                    private val repos: MinSideRepositories, val env: Environment) {

    private val log = getLogger(javaClass)

    @Transactional
    fun opprettBeskjed(type: MinSideNotifikasjonType,
                       eventId: UUID,
                       fnr: Fødselsnummer,
                       tekst: String, eksternNotifikasjon: Boolean = false) =
        with(cfg.beskjed) {
            if (enabled) {
                log.trace("Oppretter Min Side beskjed for $fnr, ekstern nofifikasjon $eksternNotifikasjon og eventid $eventId")
                dittNav.send(ProducerRecord(topic,
                        key(type.skjemaType, eventId, fnr),
                        beskjed("$tekst ($eventId)", type, eksternNotifikasjon)))
                    .addCallback(SendCallback("opprett beskjed med eventid $eventId"))
                log.trace("Oppretter Min Side beskjed i DB")
                repos.beskjeder.save(Beskjed(fnr = fnr.fnr, eventid = eventId)).also {
                    log.trace(CONFIDENTIAL, "Opprettet Ditt Nav beskjed $it i DB")
                }.eventid
            }
            else {
                log.info("Sender ikke opprett beskjed til Ditt Nav for $fnr")
                null
            }
        }

    @Transactional
    fun opprettOppgave(type: MinSideNotifikasjonType,
                       fnr: Fødselsnummer,
                       eventId: UUID,
                       tekst: String,
                       eksternNotifikasjon: Boolean = true) =
        with(cfg.oppgave) {
            if (enabled) {
                log.trace("Oppretter Min Side oppgave for $fnr, ekstern notifikasjon $eksternNotifikasjon og eventid $eventId")
                with(key(type.skjemaType, eventId, fnr)) {
                    dittNav.send(ProducerRecord(topic,
                            this,
                            oppgave("$tekst ($eventId)", type, eventId, eksternNotifikasjon)))
                        .addCallback(SendCallback("opprett oppgave med eventid $eventId"))
                    repos.oppgaver.save(Oppgave(fnr = fnr.fnr, eventid = eventId)).also {
                        log.trace(CONFIDENTIAL, "Opprettet Min Side oppgave $it i DB")
                    }.eventid
                }
            }
            else {
                log.info("Sender ikke opprett oppgave til Min Side for $fnr")
                null
            }
        }

    @Transactional
    fun avsluttOppgave(type: SkjemaType, fnr: Fødselsnummer, eventId: UUID) =
        with(cfg) {
            if (oppgave.enabled) {
                dittNav.send(ProducerRecord(done, key(type, eventId, fnr), done()))
                    .addCallback(SendCallback("avslutt oppgave med eventid $eventId"))
                log.trace("Setter oppgave done i DB for eventId $eventId")
                repos.oppgaver.findByEventid(eventId)?.let { it.done = true }
                    ?: log.warn("Kunne ikke sette beskjed med eventid $eventId for fnr $fnr til done i DB, ingen rader oppdatert")
            }
            else {
                log.info("Sender ikke avslutt oppgave til Ditt Nav for $fnr")
            }
        }

    @Transactional
    fun avsluttBeskjed(type: SkjemaType, fnr: Fødselsnummer, eventId: UUID) =
        with(cfg) {
            if (beskjed.enabled) {
                dittNav.send(ProducerRecord(done, key(type, eventId, fnr), done()))
                    .addCallback(SendCallback("avslutt beskjed med eventid $eventId"))
                log.trace("Setter beskjed done i DB for eventid $eventId")
                repos.beskjeder.findByEventid(eventId)?.let { it.done = true }
                    ?: log.warn("Kunne ikke sette beskjed med eventid $eventId for fnr $fnr til done i DB, ingen rader oppdatert")
            }
            else {
                log.info("Sender ikke avslutt beskjed til Min Side for beskjed for $fnr")
            }
        }

    private fun beskjed(tekst: String, type: MinSideNotifikasjonType, eksternNotifikasjon: Boolean) =
        with(cfg.beskjed) {
            BeskjedInputBuilder()
                .withSikkerhetsnivaa(sikkerhetsnivaa)
                .withTidspunkt(now(UTC))
                .withSynligFremTil(now(UTC).plus(varighet))
                .withLink(type.link(cfg.backlinks).toURL())
                .withTekst(tekst)
                .withEksternVarsling(eksternNotifikasjon)
                .withPrefererteKanaler(*preferertekanaler.toTypedArray())
                .build().also { m ->
                    log.trace(CONFIDENTIAL,
                            "Melding ${m.tekst}, prefererte kanaler ${m.prefererteKanaler} og ekstern notifikasjon ${m.eksternVarsling}")
                }
        }

    private fun oppgave(tekst: String, type: MinSideNotifikasjonType, eventId: UUID, eksternNotifikasjon: Boolean) =
        with(cfg.oppgave) {
            OppgaveInputBuilder()
                .withSikkerhetsnivaa(sikkerhetsnivaa)
                .withTidspunkt(now(UTC))
                .withSynligFremTil(now(UTC).plus(varighet))
                .withLink(type.link(cfg.backlinks, eventId).toURL())
                .withTekst(tekst)
                .withEksternVarsling(eksternNotifikasjon)
                .withPrefererteKanaler(*preferertekanaler.toTypedArray())
                .build().also { o ->
                    log.trace(CONFIDENTIAL,
                            "Oppgave  ${o.tekst}, prefererte kanaler ${o.prefererteKanaler} og ekstern notifikasjon ${o.eksternVarsling}")
                }
        }

    private fun done() =
        DoneInputBuilder()
            .withTidspunkt(now(UTC))
            .build()

    private fun key(type: SkjemaType, eventId: UUID, fnr: Fødselsnummer) =
        with(cfg) {
            NokkelInputBuilder()
                .withFodselsnummer(fnr.fnr)
                .withEventId("$eventId")
                .withGrupperingsId(type.name)
                .withAppnavn(app)
                .withNamespace(namespace)
                .build().also {
                    log.info(CONFIDENTIAL, "Key for Ditt Nav $type er $it")
                }
        }
}

data class MinSideNotifikasjonType private constructor(val skjemaType: SkjemaType,
                                                       private val ctx: MinSideBacklinkContext) {

    private enum class MinSideBacklinkContext {
        MINAAP,
        SØKNAD
    }

    fun link(cfg: BacklinksConfig, eventId: UUID? = null) =
        when (skjemaType) {
            STANDARD -> when (ctx) {
                MINAAP -> eventId?.let { fromUri(cfg.innsyn).queryParam("eventId", it).build().toUri() }
                    ?: cfg.innsyn

                SØKNAD -> cfg.standard
            }

            UTLAND -> when (ctx) {
                MINAAP -> cfg.innsyn
                SØKNAD -> cfg.utland
            }
        }

    companion object {
        val MINAAPSTD = MinSideNotifikasjonType(STANDARD, MINAAP)
        val MINAAPUTLAND = MinSideNotifikasjonType(UTLAND, MINAAP)
        val SØKNADSTD = MinSideNotifikasjonType(STANDARD, SØKNAD)
        val SØKNADUTLAND = MinSideNotifikasjonType(UTLAND, SØKNAD)

    }
}