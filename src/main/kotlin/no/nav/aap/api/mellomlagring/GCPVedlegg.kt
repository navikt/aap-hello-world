package no.nav.aap.api.mellomlagring

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobId.*
import com.google.cloud.storage.BlobInfo.newBuilder
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.BlobField.CONTENT_TYPE
import com.google.cloud.storage.Storage.BlobField.METADATA
import com.google.cloud.storage.Storage.BlobGetOption.fields
import no.nav.aap.api.felles.Fødselsnummer
import no.nav.boot.conditionals.ConditionalOnGCP
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.multipart.MultipartFile
import java.net.URI
import java.util.*
import java.util.Objects.hash


@ConditionalOnGCP
class GCPVedlegg(@Value("\${mellomlagring.bucket:aap-vedlegg}") private val bøtte: String, private val storage: Storage)  {

        fun lagreVedlegg(fnr: Fødselsnummer, vedlegg: MultipartFile) =
         with(vedlegg) {
             val uuid = UUID.randomUUID()
             val doc = "${hash(fnr, uuid)}"
             storage.create(
                     newBuilder(of(bøtte, doc))
                         .setContentType(contentType)
                         .setMetadata(mapOf(FILNAVN to originalFilename, FNR to fnr.fnr))
                         .build(), bytes)
              uuid
         }

    fun lesVedlegg(fnr: Fødselsnummer, uuid: UUID) = storage.get(bøtte, "${hash(fnr, uuid)}", fields(METADATA, CONTENT_TYPE))

    fun slettVedlegg(fnr: Fødselsnummer, uuid: UUID) = storage.delete(of(bøtte, "${hash(fnr, uuid)}"))


    companion object {
         const val FILNAVN = "filnavn"
         const val FNR = "fnr"

    }
}