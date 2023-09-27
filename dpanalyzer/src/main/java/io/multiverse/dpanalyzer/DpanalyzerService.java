package io.multiverse.dpanalyzer;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * A service interface managing Dpanalyzer.
 */
@VertxGen
@ProxyGen
public interface DpanalyzerService {

	/**
	 * The name of the event bus service.
	 */
	String SERVICE_NAME = "dpanalyzer-eb-service";

	/**
	 * The address on which the service is published.
	 */
	String SERVICE_ADDRESS = "service.dpanalyzer";

	String FROTNEND_ADDRESS = "mvs.to.frontend";

	String EVENT_ADDRESS = "dpanalyzer.event";

	@Fluent	
	DpanalyzerService initializePersistence(Handler<AsyncResult<Void>> resultHandler);
}