package no.nav.aap.api.oppslag.kontaktinformasjon

import io.micrometer.observation.annotation.Observed
import org.springframework.stereotype.Component

@Component
@Observed
class KRRClient(private val adapter: KRRWebClientAdapter) {
    fun kontaktInfo() = adapter.kontaktInformasjon()
}