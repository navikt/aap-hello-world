package no.nav.aap.api.pdl;

import graphql.kickstart.spring.webclient.boot.GraphQLErrorsException;
import graphql.kickstart.spring.webclient.boot.GraphQLWebClient;
import no.nav.aap.api.rest.AbstractWebClientConnection;
import no.nav.aap.api.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.function.Supplier;

import static no.nav.aap.api.pdl.PdlClientConfig.PDL_USER;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;

@Component
public class PDLConnection extends AbstractWebClientConnection {
    private static final Logger LOG = LoggerFactory.getLogger(PDLConnection.class);
    private static final String IDENT = "ident";
    private static final String NAVN_QUERY = "query-navn.graphql";

    private final GraphQLWebClient userClient;
    private final TokenUtil tokenUtil;
    private final PDLErrorHandler errorHandler;

    PDLConnection(@Qualifier(PDL_USER) GraphQLWebClient userClient, @Qualifier(PDL_USER) WebClient webClient, PDLConfig cfg, TokenUtil tokenUtil, PDLErrorHandler errorHandler) {
        super(webClient,cfg);
        this.userClient = userClient;
        this.tokenUtil = tokenUtil;
        this.errorHandler = errorHandler;
    }

    public PDLNavn hentNavn() {
        return oppslagNavn(tokenUtil.getSubject());
    }

    public PDLNavn oppslagNavn(String id) {
        return oppslag(() -> userClient.post(NAVN_QUERY, idFra(id), PDLWrappedNavn.class).block(), "navn")
                .navn().stream().findFirst().orElse(null);
    }

    private static Map<String, Object> idFra(String id) {
        return Map.of(IDENT, id);
    }
    private <T> T oppslag(Supplier<T> oppslag, String type) {
        try {
            LOG.info("PDL oppslag {}", type);
            var res = oppslag.get();
            LOG.trace("PDL oppslag {} respons={}", type, res);
            LOG.info("PDL oppslag {} OK", type);
            return res;
        } catch (GraphQLErrorsException e) {
            LOG.warn("PDL oppslag {} feilet", type, e);
            return errorHandler.handleError(e);
        } catch (Exception e) {
            LOG.warn("PDL oppslag {} feilet med uventet feil", type, e);
            throw e;
        }
    }
    @Override
    public String ping() {
        LOG.trace("Pinger {}", pingEndpoint():
        return webClient
                .options()
                .uri(pingEndpoint())
                .accept(APPLICATION_JSON, TEXT_PLAIN)
                .retrieve()
                .toEntity(String.class)
                .block()
                .getBody();
    }
}


