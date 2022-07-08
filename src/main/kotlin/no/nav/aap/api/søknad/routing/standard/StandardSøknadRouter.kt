package no.nav.aap.api.søknad.routing.standard

import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.felles.SkjemaType.STANDARD
import no.nav.aap.api.oppslag.pdl.PDLClient
import no.nav.aap.api.søknad.brukernotifikasjoner.DittNavClient
import no.nav.aap.api.søknad.joark.JoarkRouter
import no.nav.aap.api.søknad.mellomlagring.Mellomlager
import no.nav.aap.api.søknad.mellomlagring.dokument.DokumentInfo
import no.nav.aap.api.søknad.mellomlagring.dokument.Dokumentlager
import no.nav.aap.api.søknad.model.Kvittering
import no.nav.aap.api.søknad.model.StandardSøknad
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Component
import java.util.*

@Component
class StandardSøknadRouter(private val joarkRouter: JoarkRouter,
                           private val pdl: PDLClient,
                           private val finalizer: StandardSøknadAvslutter,
                           private val vlRouter: StandardSøknadVLRouter) {

    fun route(søknad: StandardSøknad) =
        with(pdl.søkerMedBarn()) outer@{
            with(joarkRouter.route(søknad, this)) {
                vlRouter.route(søknad, this@outer, journalpostId)
                finalizer.avslutt(søknad, this@outer.fnr, pdf)
            }
        }
}

@Component
class StandardSøknadAvslutter(private val dittnav: DittNavClient,
                              private val dokumentLager: Dokumentlager,
                              private val mellomlager: Mellomlager) {
    fun avslutt(søknad: StandardSøknad, fnr: Fødselsnummer, pdf: ByteArray) =
        dokumentLager.slettDokumenter(søknad).run {
            mellomlager.slett(STANDARD)
            dittnav.opprettBeskjed(STANDARD, UUID.randomUUID(), fnr, "Vi har mottatt ${STANDARD.tittel}")
            Kvittering(dokumentLager.lagreDokument(DokumentInfo(pdf, APPLICATION_PDF_VALUE, "kvittering.pdf")))
        }
}