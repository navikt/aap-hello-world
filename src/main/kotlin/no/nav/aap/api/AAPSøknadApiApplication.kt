package no.nav.aap.api

import com.google.cloud.spring.autoconfigure.pubsub.GcpPubSubAutoConfiguration
import no.nav.boot.conditionals.Cluster.Companion.profiler
import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Import
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.web.config.EnableSpringDataWebSupport
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(exclude= [ErrorMvcAutoConfiguration::class])
@EnableJwtTokenValidation(ignore = ["org.springdoc", "org.springframework"])
@EnableOAuth2Client(cacheEnabled = true)
@ConfigurationPropertiesScan
@EnableRetry
@EnableKafka
@EnableCaching
@EnableJpaAuditing
@EnableSpringDataWebSupport
@EnableScheduling
@Import(GcpPubSubAutoConfiguration::class)
class AAPSøknadApiApplication

@Autowired
private lateinit var env: ConfigurableEnvironment

    fun main(args: Array<String>) {
        runApplication<AAPSøknadApiApplication>(*args) {
            setAdditionalProfiles(*profiler())
            applicationStartup = BufferingApplicationStartup(4096)
        }
    }