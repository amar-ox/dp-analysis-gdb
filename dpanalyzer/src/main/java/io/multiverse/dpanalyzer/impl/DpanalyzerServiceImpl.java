package io.multiverse.dpanalyzer.impl;

import java.io.IOException;
import java.util.List;

import io.multiverse.dpanalyzer.DpanalyzerService;
import io.multiverse.dpanalyzer.apverifier.Network;
import io.multiverse.dpanalyzer.common.Neo4jWrapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Implementation of Dpanalyzer API
 */
public class DpanalyzerServiceImpl extends Neo4jWrapper implements DpanalyzerService {

	private static final Logger logger = LoggerFactory.getLogger(DpanalyzerServiceImpl.class);

	private static final String MAIN_DB = "neo4j";

	public DpanalyzerServiceImpl(Vertx vertx, JsonObject config) {
		super(vertx, config);
	}

	@Override
	public DpanalyzerService initializePersistence(Handler<AsyncResult<Void>> resultHandler) {
		loadExampleNetwork(resultHandler);
		return this;
	}
	
	private void loadExampleNetwork(Handler<AsyncResult<Void>> resultHandler) {
		vertx.<List<String>>executeBlocking(future -> {
			try {
				long start = System.nanoTime();
				Network n = new Network("st");
				long end = System.nanoTime();
		    	System.out.println("APV model done after " + (end - start)/1000000.0 + " ms");
				future.complete(n.getQueries());
			} catch (IOException e) {
				e.printStackTrace();
				future.fail(e.getCause());
			}
		}, res -> {
			if (res.succeeded()) {
				long start = System.nanoTime();
				bulkExecute(MAIN_DB, res.result(), db -> {
					if (db.succeeded()) {
						long end = System.nanoTime();
				    	System.out.println("DB done after " + (end - start)/1000000.0 + " ms");
						resultHandler.handle(Future.succeededFuture());
						logger.info("Graph created.");
					} else {
						logger.error(db.cause());
						resultHandler.handle(Future.failedFuture(db.cause()));
					}
				});
			} else {
				resultHandler.handle(Future.failedFuture(res.cause()));	
			}
		});
	}
}