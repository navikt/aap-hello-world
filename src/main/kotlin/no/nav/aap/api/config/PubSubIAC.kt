package no.nav.aap.api.config

import com.google.cloud.pubsub.v1.TopicAdminClient
import com.google.cloud.spring.pubsub.PubSubAdmin
import com.google.cloud.spring.pubsub.support.PubSubTopicUtils
import com.google.cloud.storage.NotificationInfo
import com.google.cloud.storage.NotificationInfo.EventType.OBJECT_DELETE
import com.google.cloud.storage.NotificationInfo.EventType.OBJECT_FINALIZE
import com.google.cloud.storage.NotificationInfo.PayloadFormat.JSON_API_V1
import com.google.cloud.storage.Storage
import com.google.iam.v1.Binding
import com.google.iam.v1.GetIamPolicyRequest
import com.google.iam.v1.Policy
import com.google.iam.v1.SetIamPolicyRequest
import no.nav.aap.api.søknad.mellomlagring.BucketsConfig
import no.nav.aap.util.LoggerUtil
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.stereotype.Component

@Component
class PubSubIAC(private val cfgs: BucketsConfig, private val storage: Storage, private val admin: PubSubAdmin) :
    InitializingBean {

    private val log = LoggerUtil.getLogger(javaClass)
    override fun afterPropertiesSet() = init()

    private fun init() {
        with(cfgs) {
            if (!harTopic()) {
                lagTopic()
            }
            else {
                log.trace("Topic ${mellom.subscription.topic} finnes allerede i $id")
            }
            if (!harSubscription()) {
                lagSubscription()
            }
            else {
                log.trace("Subscription ${mellom.subscription.navn} finnes allerede for topic ${mellom.subscription.topic}")
            }


            if (!harNotifikasjon()) {
                lagNotifikasjon()
            }
            else {
                log.trace("$mellomBøtte har allerede en notifikasjon på ${mellom.subscription.topic}")
            }
            setPubSubAdminPolicyForBucketServiceAccountOnTopic(mellom.subscription.topic)  //Idempotent
        }
    }

    private fun harTopic() =
        admin.getTopic(cfgs.mellom.subscription.topic) != null

    private fun lagTopic() =
        admin.createTopic(cfgs.mellom.subscription.topic).also {
            log.trace("Lagd topic ${it.name}")
        }

    private fun harNotifikasjon() =
        listTopicForNotifikasjon().find { it.substringAfterLast('/') == cfgs.mellom.subscription.topic } != null

    private fun lagNotifikasjon() =
        with(cfgs) {
            storage.createNotification(mellomBøtte,
                    NotificationInfo.newBuilder(PubSubTopicUtils.toTopicName(mellom.subscription.topic, id).toString())
                        .setEventTypes(OBJECT_FINALIZE, OBJECT_DELETE)
                        .setPayloadFormat(JSON_API_V1)
                        .build()).also {
                log.trace("Lagd notifikasjon ${it.notificationId} for topic ${mellom.subscription.topic}")
            }
        }

    private fun setPubSubAdminPolicyForBucketServiceAccountOnTopic(topic: String) =
        TopicAdminClient.create().use { c ->
            with(topic) {
                c.setIamPolicy(SetIamPolicyRequest.newBuilder()
                    .setResource(this)
                    .setPolicy(Policy.newBuilder(c.getIamPolicy(GetIamPolicyRequest.newBuilder()
                        .setResource(this).build())).addBindings(Binding.newBuilder()
                        .setRole("roles/pubsub.publisher")
                        .addMembers("serviceAccount:${storage.getServiceAccount(cfgs.id).email}")
                        .build()).build())
                    .build()).also { log.trace("Policy på $topic er ${it.bindingsList}") }
            }
        }

    private fun listTopicForNotifikasjon() =
        with(cfgs) {
            storage.listNotifications(mellomBøtte)
                .map { it.topic }
        }

    private fun harSubscription() =
        admin.getSubscription(cfgs.mellom.subscription.navn) != null

    private fun lagSubscription() =
        with(cfgs.mellom.subscription) {
            admin.createSubscription(navn, topic)
                .also {
                    log.trace("Lagd pull subscription ${it.name}")
                }
        }

    @Component
    @Endpoint(id = "iac")
    class IACEndpoint(private val iac: PubSubIAC, private val cfgs: BucketsConfig) {
        @ReadOperation
        fun iacOperation() =
            with(cfgs) {
                mutableMapOf("bucket" to mellomBøtte,
                        "topic" to mellom.subscription.topic,
                        "subscription" to mellom.subscription.navn,
                        "notification" to iac.listTopicForNotifikasjon())
                    .apply {
                        putAll(mapOf("ring" to ringNavn,
                                "nøkkel" to nøkkelNavn))
                    }
            }
    }
}