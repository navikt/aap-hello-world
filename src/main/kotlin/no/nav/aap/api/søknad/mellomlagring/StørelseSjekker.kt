package no.nav.aap.api.søknad.mellomlagring

import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.BlobListOption.currentDirectory
import com.google.cloud.storage.Storage.BlobListOption.prefix
import no.nav.aap.api.error.Substatus.SIZE
import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.søknad.mellomlagring.BucketConfig.VedleggBucketConfig
import no.nav.aap.api.søknad.mellomlagring.dokument.DokumentInfo
import no.nav.aap.util.LoggerUtil
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize

@Component
class StørelseSjekker(private val lager: Storage) {

    val log = LoggerUtil.getLogger(javaClass)
    fun sjekkStørrelse(cfg: VedleggBucketConfig, fnr: Fødselsnummer, dokument: DokumentInfo) =
        with(cfg) {
            val sum = lager.list(navn, prefix("${fnr.fnr}/"), currentDirectory()).iterateAll().sumOf { it.size }
            if (sum + dokument.size > maxsum.toBytes()) {
                throw DokumentException("Opplasting av  $dokument tillates ikke, har allerede lastet opp ${DataSize.ofBytes(sum)}, max pr bruker er er $maxsum", null, SIZE)
            }
            else {
                log.trace("Opplasting av ${dokument.size} tillates, størrelse av vedlegg i bøtte er $sum")
            }
        }
}