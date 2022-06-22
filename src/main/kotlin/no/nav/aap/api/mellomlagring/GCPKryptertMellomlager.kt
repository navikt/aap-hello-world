package no.nav.aap.api.mellomlagring

import com.google.cloud.storage.BlobId.of
import com.google.cloud.storage.BlobInfo.newBuilder
import com.google.cloud.storage.Storage
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates.get
import com.google.crypto.tink.KeysetHandle.generateNew
import com.google.crypto.tink.aead.KmsEnvelopeAeadKeyManager.createKeyTemplate
import no.nav.aap.api.felles.Fødselsnummer
import no.nav.aap.api.felles.SkjemaType
import no.nav.aap.util.LoggerUtil.getLogger
import no.nav.boot.conditionals.ConditionalOnGCP
import no.nav.boot.conditionals.EnvUtil.CONFIDENTIAL
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import java.nio.charset.StandardCharsets.UTF_8

@ConditionalOnGCP
@Primary
internal class GCPKryptertMellomlager(private val config: GCPBucketConfig,
                                      private val lager: Storage,
                                      private val aead: Aead) : Mellomlager {
    val log = getLogger(javaClass)
    
    override fun lagre(fnr: Fødselsnummer, type: SkjemaType, value: String) =
        lager.create(newBuilder(of(config.bucket, key(fnr, type)))
            .setContentType(APPLICATION_JSON_VALUE).build(),
                aead.encrypt(value.toByteArray(UTF_8), fnr.fnr.toByteArray(UTF_8)))
            .blobId.toGsUtilUri()
            .also { log.trace(CONFIDENTIAL, "Lagret $value kryptert for $fnr") }

    override fun les(fnr: Fødselsnummer, type: SkjemaType) =
        lager.get(config.bucket, key(fnr, type))?.let {
            String(aead.decrypt(it.getContent(), fnr.fnr.toByteArray(UTF_8))).also {
                log.trace(CONFIDENTIAL, "Lest $it for $fnr")
            }
        }

    override fun slett(fnr: Fødselsnummer, type: SkjemaType) =
        lager.delete(of(config.bucket, key(fnr, type)))
}