package no.nav.aap.api.oppslag.konto

import io.micrometer.observation.annotation.Observed
import org.springframework.stereotype.Component

@Component
@Observed(contextualName = "Konto")
class KontoClient(private val adapter: KontoWebClientAdapter) {
    fun kontoInfo() = adapter.kontoInfo()
}