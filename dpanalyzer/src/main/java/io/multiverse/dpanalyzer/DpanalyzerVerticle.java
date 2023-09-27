package io.multiverse.dpanalyzer;

import static io.multiverse.dpanalyzer.DpanalyzerService.SERVICE_ADDRESS;

import io.multiverse.dpanalyzer.api.RestDpanalyzerAPIVerticle;
import io.multiverse.dpanalyzer.common.BaseMicroserviceVerticle;
import io.multiverse.dpanalyzer.impl.DpanalyzerServiceImpl;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.serviceproxy.ServiceBinder;


/**
 * A verticle publishing the Dpanalyzer service.
 */
public class DpanalyzerVerticle extends BaseMicroserviceVerticle {

	@Override
	public void start(Future<Void> future) throws Exception {
		super.start();

		// create the service instance
		DpanalyzerService service = new DpanalyzerServiceImpl(vertx, config());

		// register the service proxy on event bus
		new ServiceBinder(vertx)
				.setAddress(SERVICE_ADDRESS)
				.register(DpanalyzerService.class, service);

		initDatabase(service)
				.compose(r -> deployRestVerticle(service))
				.onComplete(future);
	}

	private Future<Void> initDatabase(DpanalyzerService service) {
		Promise<Void> initPromise = Promise.promise();
		service.initializePersistence(initPromise);
		return initPromise.future();
	}

	private Future<Void> deployRestVerticle(DpanalyzerService service) {
		Promise<String> promise = Promise.promise();
		vertx.deployVerticle(new RestDpanalyzerAPIVerticle(service),
				new DeploymentOptions().setConfig(config()), promise);
		return promise.future().map(r -> null);
	}
}