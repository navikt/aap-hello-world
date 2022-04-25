package no.nav.aap.api.søknad.joark

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.storage.Blob
import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.felles.error.IntegrationException
import no.nav.aap.api.mellomlagring.Dokumentlager
import no.nav.aap.api.søknad.joark.pdf.PDFClient
import no.nav.aap.api.søknad.model.SkjemaType.STANDARD
import no.nav.aap.api.søknad.model.SkjemaType.UTLAND
import no.nav.aap.api.søknad.model.StandardSøknad
import no.nav.aap.api.søknad.model.Søker
import no.nav.aap.api.søknad.model.Utbetaling.VedleggAware
import no.nav.aap.api.søknad.model.UtlandSøknad
import no.nav.aap.joark.AvsenderMottaker
import no.nav.aap.joark.Bruker
import no.nav.aap.joark.Dokument
import no.nav.aap.joark.DokumentVariant
import no.nav.aap.joark.Filtype.Companion.of
import no.nav.aap.joark.Filtype.JSON
import no.nav.aap.joark.Journalpost
import no.nav.aap.joark.VariantFormat.ORIGINAL
import no.nav.aap.joark.asPDFVariant
import no.nav.aap.util.LoggerUtil
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Service
import java.util.Base64.getEncoder

@Service
class JoarkRouter(private val joark: JoarkClient, private val pdf: PDFClient, private val lager: Dokumentlager, private val mapper: ObjectMapper)  {

    private val log = LoggerUtil.getLogger(javaClass)
     fun route(søknad: StandardSøknad, søker: Søker) =
         with(pdf.generate(søker, søknad)) {
            Pair(lagrePdf(this,søker.fødselsnummer), joark.journalfør(journalpostFra(søknad, søker,asPDFVariant())) ?: throw IntegrationException("Kunne ikke journalføre søknad"))
        }.also { slettVedlegg(søknad,søker.fødselsnummer) }



    fun route(søknad: UtlandSøknad, søker: Søker) =
        with(pdf.generate(søker, søknad)) {
            Pair(lagrePdf(this, søker.fødselsnummer), joark.journalfør(journalpostFra(søknad, søker,asPDFVariant())) ?: throw IntegrationException("Kunne ikke journalføre søknad"))
        }
    private fun lagrePdf(bytes: ByteArray, fnr: Fødselsnummer) =
        lager.lagreDokument(fnr, bytes, APPLICATION_PDF_VALUE, "kvittering.pdf")
            .also { "Lagret pdf med uuid $it" }

    private fun journalpostFra(søknad: StandardSøknad, søker: Søker, pdfDokument: DokumentVariant) =
        Journalpost(dokumenter = dokumenterFra(søknad, søker,pdfDokument),
                tittel = STANDARD.tittel,
                avsenderMottaker = AvsenderMottaker(søker.fødselsnummer,
                        navn = søker.navn.navn),
                bruker = Bruker(søker.fødselsnummer))
            .also { log.trace("Journalpost er $it") }


    private fun journalpostFra(søknad: UtlandSøknad, søker: Søker,pdfDokument: DokumentVariant)  =
        Journalpost(dokumenter = dokumenterFra(søknad, søker,pdfDokument),
                tittel = UTLAND.tittel,
                avsenderMottaker = AvsenderMottaker(søker.fødselsnummer,
                        navn = søker.navn.navn),
                bruker = Bruker(søker.fødselsnummer))
            .also { log.trace("Journalpost er $it") }

    private fun dokumenterFra(søknad: StandardSøknad, søker: Søker,pdfDokument: DokumentVariant) =
        listOf(Dokument(STANDARD.tittel,
                STANDARD.kode,
                listOf(jsonDokument(søknad), pdfDokument)
                        + vedleggFor(søknad.utbetalinger?.stønadstyper, søker.fødselsnummer)
                        + vedleggFor(søknad.utbetalinger?.andreUtbetalinger, søker.fødselsnummer)
                    .also { log.trace("${it.size} dokumentvarianter ($it)") }))
            .also { log.trace("Dokument til JOARK $it") }
    private fun dokumenterFra(søknad: UtlandSøknad, søker: Søker,pdfDokument: DokumentVariant) =
        listOf(Dokument(UTLAND.tittel,
                UTLAND.kode,
                listOf(jsonDokument(søknad),pdfDokument)
                    .also { log.trace("${it.size} dokumentvarianter ($it)") }))
            .also { log.trace("Dokument til JOARK $it") }
    private fun jsonDokument(søknad: StandardSøknad) =
        DokumentVariant(JSON, søknad.toEncodedJson(mapper), ORIGINAL)

    private fun jsonDokument(søknad: UtlandSøknad) =
        DokumentVariant(JSON, søknad.toEncodedJson(mapper), ORIGINAL)

    private fun vedleggFor(utbetalinger: List<VedleggAware>?, fnr: Fødselsnummer) =
        utbetalinger
            ?.mapNotNull { it.vedlegg }
            ?.mapNotNull { lager.lesDokument(fnr, it) }
            ?.map { it.asDokumentVariant() }
            .orEmpty()

    private fun slettVedlegg(søknad: StandardSøknad, fnr: Fødselsnummer) {
        slett(søknad.utbetalinger?.stønadstyper,fnr)
        slett(søknad.utbetalinger?.andreUtbetalinger,fnr)
    }
    private fun slett(utbetalinger: List<VedleggAware>?, fnr: Fødselsnummer) =
        utbetalinger
            ?.mapNotNull { it.vedlegg }
            ?.forEach { lager.slettDokument(fnr, it) }
    private fun Blob.asDokumentVariant() =
        DokumentVariant(of(contentType), getEncoder().encodeToString(getContent()))
}