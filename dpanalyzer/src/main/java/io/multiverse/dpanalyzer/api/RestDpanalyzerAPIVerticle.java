package io.multiverse.dpanalyzer.api;

import io.multiverse.dpanalyzer.DpanalyzerService;
import io.multiverse.dpanalyzer.common.RestAPIVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * This verticle exposes REST API endpoints to process Dpanalyzer operation
 */
public class RestDpanalyzerAPIVerticle extends RestAPIVerticle {

	private static final Logger logger = LoggerFactory.getLogger(RestDpanalyzerAPIVerticle.class);

	public static final String SERVICE_NAME = "dpanalyzer-rest-api";

	private static final String API_VERSION = "/v";

	private DpanalyzerService service;

	public RestDpanalyzerAPIVerticle(DpanalyzerService service) {
		this.service = service;
	}

	@Override
	public void start(Future<Void> future) throws Exception {
		super.start();
		final Router router = Router.router(vertx);

		// body handler
		router.route().handler(BodyHandler.create());

		// version
		router.get(API_VERSION).handler(this::apiVersion);

		// get HTTP host and port from configuration, or use default value
		String host = config().getString("dpanalyzer.http.address", "0.0.0.0");
		int port = config().getInteger("dpanalyzer.http.port", 7070);

		// create HTTP server and publish REST service
		createHttpServer(router, host, port)
				.compose(serverCreated -> publishHttpEndpoint(SERVICE_NAME, host, port))
				.onComplete(future);
	}

	/* API version */
	private void apiVersion(RoutingContext context) {
		context.response().end(new JsonObject()
				.put("name", SERVICE_NAME)
				.put("version", "v1").encodePrettily());
	}
}
