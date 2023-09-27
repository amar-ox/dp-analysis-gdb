package io.multiverse.dpanalyzer.common;

import static org.neo4j.driver.internal.summary.InternalSummaryCounters.EMPTY_STATS;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.neo4j.driver.AccessMode;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.async.AsyncTransaction;
import org.neo4j.driver.async.ResultCursor;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.neo4j.driver.internal.summary.InternalSummaryCounters;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;

import org.neo4j.driver.Session;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Helper and wrapper class for Neo4j database services.
 */
public class Neo4jWrapper {

	private static final Logger logger = LoggerFactory.getLogger(Neo4jWrapper.class);

	private Driver driver;
	protected final Vertx vertx;
	
	protected final String dbUser;
	protected final String dbPassword;
	
	private AsyncTransaction tx;
    private AsyncSession txSession;

	public Neo4jWrapper(Vertx vertx, JsonObject config) {
		this.vertx = vertx;
		this.dbUser = config.getString("user");
		this.dbPassword = config.getString("password");
		this.driver = GraphDatabase.driver(config.getString("url"), AuthTokens.basic(dbUser, dbPassword));
		driver.verifyConnectivityAsync().thenAccept(r -> {
        	logger.info("Connected to neo4j");
        	return;
        });
	}

	public void findOne(String db, String query, Handler<AsyncResult<JsonObject>> resultHandler) {
		findOne(db, query, new JsonObject(), resultHandler);
    }
	public void findOne(String db, String query, JsonObject params, Handler<AsyncResult<JsonObject>> resultHandler) {
		AsyncSession session = driver.asyncSession(configBuilder(db, AccessMode.WRITE));
		Context context = vertx.getOrCreateContext();
		session.writeTransactionAsync(tx -> tx.runAsync(query, params.getMap()).thenCompose(ResultCursor::singleAsync))
			    .whenComplete(wrapCallbackSingle(context, resultHandler))
				.thenCompose(ignore -> session.closeAsync());
    }
	
	public void find(String db, String query, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
		find(db, query, new JsonObject(), resultHandler);
    }
	public void find(String db, String query, JsonObject params, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
		// TODO: check driver state
		AsyncSession session = driver.asyncSession(configBuilder(db, AccessMode.READ));
		Context context = vertx.getOrCreateContext();
		session.writeTransactionAsync(tx -> tx.runAsync(query, params.getMap()).thenCompose(ResultCursor::listAsync))
			    .whenComplete(wrapCallbackList(context, resultHandler))
				.thenCompose(ignore -> session.closeAsync());
    }

	public void execute(String db, String query, Handler<AsyncResult<JsonObject>> resultHandler) {
		execute(db, query, new JsonObject(), resultHandler);
	}
	public void execute(String db, String query, JsonObject params, Handler<AsyncResult<JsonObject>> resultHandler) {
		AsyncSession session = driver.asyncSession(configBuilder(db, AccessMode.WRITE));
		Context context = vertx.getOrCreateContext();
		session.writeTransactionAsync(tx -> tx.runAsync(query, params.getMap()).thenCompose(ResultCursor::consumeAsync))
			    .whenComplete(wrapCallbackSummary(context, resultHandler))
				.thenCompose(ignore -> session.closeAsync());
	}

	public void delete(String db, String query, Handler<AsyncResult<JsonObject>> resultHandler) {
		delete(db, query, new JsonObject(), resultHandler);
	}
	public void delete(String db, String query, JsonObject params, Handler<AsyncResult<JsonObject>> resultHandler) {
		AsyncSession session = driver.asyncSession(configBuilder(db, AccessMode.WRITE));
		Context context = vertx.getOrCreateContext();
		session.writeTransactionAsync(tx -> tx.runAsync(query, params.getMap())
				.thenCompose(ResultCursor::consumeAsync).thenApply(ResultSummary::counters))
			    .whenComplete(wrapCallbackSummary(context, resultHandler))
				.thenCompose(ignore -> session.closeAsync());
	}
	
	/* Transactions */
	// Create a new transaction, no automatic rollback/commit on subsequent operations
	public void beginTransaction(String db, Handler<AsyncResult<Void>> resultHandler) {
		Context context = vertx.getOrCreateContext();
        this.txSession = driver.asyncSession(configBuilder(db, AccessMode.WRITE));
        this.txSession.beginTransactionAsync().thenAccept(tx -> {
        	this.tx = tx;
            context.runOnContext(v -> resultHandler.handle(Future.succeededFuture()));
        }).exceptionally(error -> {
            context.runOnContext(v -> resultHandler.handle(Future.failedFuture(error)));
            this.txSession.closeAsync();
            return null;
        });
    }
	public void transactionExecute(String query, Handler<AsyncResult<JsonObject>> resultHandler) {
        transactionExecute(query, new JsonObject(), resultHandler);
    }
	public void transactionExecute(String query, JsonObject params, Handler<AsyncResult<JsonObject>> resultHandler) {
		Context context = vertx.getOrCreateContext();
        this.tx.runAsync(query, params.getMap())
        		.thenCompose(ResultCursor::consumeAsync)
        		.whenComplete(wrapCallbackSummary(context, resultHandler));
    }
	public void transactionFind(String query, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        transactionFind(query, new JsonObject(), resultHandler);
    }
	public void transactionFind(String query, JsonObject params, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
		Context context = vertx.getOrCreateContext();
		this.tx.runAsync(query, params.getMap())
        		.thenCompose(ResultCursor::listAsync)
        		.whenComplete(wrapCallbackList(context, resultHandler));
    }
	public void commit(Handler<AsyncResult<Void>> resultHandler) {
		tx.commitAsync()
				.whenComplete((res, err) -> {
					if (err != null) {
						resultHandler.handle(Future.failedFuture(err.getCause()));
					} else {
						resultHandler.handle(Future.succeededFuture());
					}
				})
				.thenCompose(ignore -> txSession.closeAsync());
	}
	public void rollback(Handler<AsyncResult<Void>> resultHandler) {
		tx.rollbackAsync()
				.whenComplete((res, err) -> {
					if (err != null) {
						resultHandler.handle(Future.failedFuture(err.getCause()));
					} else {
						resultHandler.handle(Future.succeededFuture());
					}
				})
				.thenCompose(ignore -> txSession.closeAsync());
	}
	
	public void createGraph(String db, List<String> queries, 
			Handler<AsyncResult<List<String>>> resultHandler) {
		beginTransaction(db, res -> {
			if (res.succeeded()) {
				CompletionStage<ResultCursor> stage = CompletableFuture.completedFuture(null);
				Instant start = Instant.now();
	            for (String query : queries) {
	                stage = stage.thenCompose(current -> tx.runAsync(query));
	                       // .thenCompose(ResultCursor::consumeAsync)
	                       // .thenApply(ResultSummary::counters)
	                       // .thenApply(counters -> aggregateResults(query, current, counters)));
	            }
	            stage.whenComplete((result, error) -> {
	            	if (error != null) {
	            		logger.info("Graph creation error: " + error.getMessage());
	            		resultHandler.handle(Future.failedFuture(error.getMessage()));
	            		rollback(ignore -> {});
	                } else {
	                	commit(done -> {
	                		if (done.succeeded()) {
	                			Instant end = Instant.now();
	    						logger.info("3- Graph creation time: " + Duration.between(start, end).toMillis() + " ms");
	                			resultHandler.handle(Future.succeededFuture(new ArrayList<String>()));
	                		} else {
	                			resultHandler.handle(Future.failedFuture(done.cause()));
	                		}
	                	});
	                }
	            });
			} else {
				resultHandler.handle(Future.failedFuture(res.cause()));
			}
		});
	}
	
	public void createGraphBlocking(String db, List<String> queries, 
			Handler<AsyncResult<List<String>>> resultHandler) {
		vertx.executeBlocking(future -> {
			Session session = driver.session(configBuilder(db, AccessMode.WRITE));
			Transaction tx = session.beginTransaction();
			tx.run(queries.get(0));
			Instant start = Instant.now();
			long avgQueryTime = 0;
			for (String query : queries.subList(1, queries.size()-1)) {
				Result res = tx.run(query);
				avgQueryTime+= res.consume().resultAvailableAfter(TimeUnit.MILLISECONDS);
			}
			Instant end = Instant.now();
			logger.info("3- Graph creation time: " + Duration.between(start, end).toMillis() + " ms");
			tx.commit();
			logger.info("4- Avg query time on server: " + avgQueryTime / (queries.size()-1) + " ms");
		}, done -> {
			if (done.succeeded()) {
				resultHandler.handle(Future.succeededFuture());
			} else {
				resultHandler.handle(Future.failedFuture(done.cause()));
			}
		});
	}

	private List<String> aggregateResults(String query, List<String> current, SummaryCounters counters) {
		if (counters.nodesCreated() == 0 
				&& counters.relationshipsCreated() == 0
				&& counters.propertiesSet() == 0
				&& counters.nodesDeleted() == 0) {
			current.add("Warning: no change after query <"+query+">");
		}
		return current;
	}
	
	/* Bulk queries */
	public void bulkExecute(String db, List<String> queries, Handler<AsyncResult<JsonObject>> resultHandler) {
	        AsyncSession session = driver.asyncSession(configBuilder(db, AccessMode.WRITE));
	        Context context = vertx.getOrCreateContext();
	        session.writeTransactionAsync(tx -> {
	            CompletionStage<SummaryCounters> stage = CompletableFuture.completedFuture(EMPTY_STATS);
	            logger.info("Executing queries...");
	            for (String query : queries) {
	                stage = stage.thenCompose(previousCounter -> tx.runAsync(query)
	                        .thenCompose(ResultCursor::consumeAsync)
	                        .thenApply(ResultSummary::counters)
	                        .thenApply(nextCounter -> AGGREGATE_COUNTERS.apply(previousCounter, nextCounter)));
	            }
	            return stage;
	        })
	        .whenComplete(wrapCallbackSummary(context, resultHandler))
	        .thenCompose(ignore -> session.closeAsync());
	 }
	 
	 private final BinaryOperator<SummaryCounters> AGGREGATE_COUNTERS = (summaryCounters, summaryCounters2) -> new InternalSummaryCounters(
			 summaryCounters.nodesCreated() + summaryCounters2.nodesCreated(),
	         summaryCounters.nodesDeleted() + summaryCounters2.nodesDeleted(),
	         summaryCounters.relationshipsCreated() + summaryCounters2.relationshipsCreated(),
	         summaryCounters.relationshipsDeleted() + summaryCounters2.relationshipsDeleted(),
	         summaryCounters.propertiesSet() + summaryCounters2.propertiesSet(),
	         summaryCounters.labelsAdded() + summaryCounters2.labelsAdded(),
	         summaryCounters.labelsRemoved() + summaryCounters2.labelsRemoved(),
             summaryCounters.indexesAdded() + summaryCounters2.indexesAdded(),
	         summaryCounters.indexesRemoved() + summaryCounters2.indexesRemoved(),
	         summaryCounters.constraintsAdded() + summaryCounters2.constraintsAdded(),
	         summaryCounters.constraintsRemoved() + summaryCounters2.constraintsRemoved(),
	         summaryCounters.systemUpdates() + summaryCounters2.systemUpdates()
	 );

	/* Result wrappers */
	private <T> BiConsumer<T, Throwable> wrapCallbackSummary(Context context, Handler<AsyncResult<JsonObject>> resultHandler) {
        return (result, error) -> {
            context.runOnContext(v -> {
                if (error != null) {
                	handleError(error, resultHandler);
                } else {
                	SummaryCounters summary;
                	if (result instanceof ResultSummary) {
                		summary = ((ResultSummary)result).counters();
                	} else {
                		summary = (SummaryCounters) result;
                	}
                	JsonObject json = new JsonObject();
                	json.put("labelsAdded", summary.labelsAdded());
                	json.put("labelsRemoved", summary.labelsRemoved());
                	json.put("nodesCreated", summary.nodesCreated());
                	json.put("nodesDeleted", summary.nodesDeleted());
                	json.put("propertiesSet", summary.propertiesSet());
                	json.put("relationshipsCreated", summary.relationshipsCreated());
                	json.put("relationshipsDeleted", summary.relationshipsDeleted());
                	json.put("constraintsAdded", summary.constraintsAdded());
                	json.put("constraintsRemoved", summary.constraintsRemoved());
                    resultHandler.handle(Future.succeededFuture(json));
                }
            });
        };
    }
	
	private BiConsumer<Record, Throwable> wrapCallbackSingle(Context context, Handler<AsyncResult<JsonObject>> resultHandler) {
        return (result, error) -> {
            context.runOnContext(v -> {
                if (error != null) {
                    handleError(error, resultHandler);
                } else {
                	JsonObject json = new JsonObject(result.asMap());
                    resultHandler.handle(Future.succeededFuture(json));
                }
            });
        };
    }
	
	private BiConsumer<List<Record>, Throwable> wrapCallbackList(Context context, Handler<AsyncResult<List<JsonObject>>> resultHandler) {
        return (result, error) -> {
            context.runOnContext(v -> {
                if (error != null) {
                	handleError(error, resultHandler);
                } else {
                	List<JsonObject> json = result.stream().map(x->new JsonObject(x.asMap())).collect(Collectors.toList());
                    resultHandler.handle(Future.succeededFuture(json));
                }
            });
        };
    }

	private SessionConfig configBuilder(String db, AccessMode am) {
		return SessionConfig.builder()
				.withDatabase(db)
				.withDefaultAccessMode(am)
				.build();
	}
	
	private <T> void handleError(Throwable error, Handler<AsyncResult<T>> resultHandler) {
	// Errors: https://neo4j.com/docs/status-codes/current/
        // Neo.ClientError.Database.DatabaseNotFound
        // Neo.ClientError.Database.ExistingDatabaseFound
        // Neo.ClientError.General.InvalidArguments
        // Neo.ClientError.Request.Invalid
		// Neo.ClientError.Schema.ConstraintValidationFailed
        // NoSuchRecordException
		if (error instanceof Neo4jException) {
			Neo4jException ne = (Neo4jException) error;
			String code = ne.code();
			logger.error("Neo4jException: " + code);
			String errorMessage = "INTERNAL";
			if (code.contains("NotFound")) {
				errorMessage = "NOT_FOUND";
			} else if (code.contains("DatabaseFound")) {
				errorMessage = "CONFLICT";
			} else if (code.contains("Invalid")) {
				errorMessage = "INVALID_ARGUMENTS";
			} else if (code.contains("Constraint")) {
				errorMessage = "CONFLICT";
			}
			resultHandler.handle(Future.failedFuture(errorMessage));
		} else if (error instanceof NoSuchRecordException) {
			logger.error("Neo4j NoSuchRecord: " + error.getMessage());
	        resultHandler.handle(Future.failedFuture("NOT_FOUND"));
		} else {
			logger.error("Exception: " + error.getMessage());
	        resultHandler.handle(Future.failedFuture("INTERNAL"));
		}
	}
}
