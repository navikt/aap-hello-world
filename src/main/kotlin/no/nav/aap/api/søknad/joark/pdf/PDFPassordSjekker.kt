package no.nav.aap.api.søknad.joark.pdf

import no.nav.aap.api.søknad.mellomlagring.DokumentException
import no.nav.aap.api.søknad.mellomlagring.dokument.DokumentInfo
import no.nav.aap.api.søknad.mellomlagring.dokument.DokumentSjekker
import no.nav.aap.api.søknad.mellomlagring.dokument.DokumentSjekker.Companion.TIKA
import no.nav.aap.util.LoggerUtil.getLogger
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Component

@Component
class PDFEncryptionChecker : DokumentSjekker {
    private val log = getLogger(javaClass)

    override fun sjekk(dokument: DokumentInfo) {
        with(dokument) {
            if (APPLICATION_PDF_VALUE == TIKA.detect(bytes)) {
                try {
                    log.trace("Sjekker om  $filnavn er passord-beskyttet")
                    PDDocument.load(bytes).use { }
                }
                catch (e: InvalidPasswordException) {
                    throw DokumentException("$filnavn er passord-beskyttet", e)
                }
                catch (e: Exception) {
                    log.warn("Uventet feil ved sjekking om $filnavn er passord-beskyttet", e)
                }
            }
            else {
                log.trace("Sjekker ikke dokumenter om type $contentType er passord-beskyttet")
            }
        }
    }
}