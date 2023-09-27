# Reachability with Java Plugin
In this example, a custom plugin (`net`) implemented in Java has been loaded into Neo4j to compute reachability.
The set of Equivalence Classes (EC) of reachability States are inserted as property.

Use the queries below to play with network reachability.


## 1. Access Neo4j
Access the Neo4j console from a browser at http://localhost:7474.

Use (user:`neo4j` password:`12345`) if prompted to connect.


## 2. Compute and store all reachability trees (Insert only final ECs)
```bash
// clean existing reachability states
MATCH (s:State) DETACH DELETE s;
```
```bash
MATCH (r:Router)-[:HAS_PORT]->(p:Port)-[:LINKS_TO]-()
WITH DISTINCT r,p
WITH collect(r.name+','+p.name) as ports
CALL net.trees.compute(ports, false, false) YIELD time
RETURN time;
```

## 3. Retrieve reachability trees
```bash
MATCH (s:State)<-[:ORIG_STATE]-()
MATCH path = (s)-[:FWD_NEXT*]->(e:State) WHERE NOT (e)-[:FWD_NEXT]->()
UNWIND nodes(path) AS n
MATCH (n)<-[]-(p:Port)<-[:HAS_PORT]-(r:Router) 
WITH path, collect({state: n, router:r, port:p}) as ports, e
RETURN REDUCE(s = '', pr IN ports | s + ' ' + pr.router.name + ',' + pr.port.name) AS paths, e.flag
```

## 4. [Optional] Generate rechability graphs (States of the same reachability tree are merged at each port)
```bash
// clean existing reachability states
MATCH (s:State) DETACH DELETE s;
```
```bash
MATCH (r:Router)-[:HAS_PORT]->(p:Port)-[:LINKS_TO]-()
WITH DISTINCT r,p
WITH collect(r.name+','+p.name) as ports
CALL net.trees.compute(ports, false, true) YIELD time
RETURN time;
```

## 5. [Optional] Compute and stream reachability trees (w/o persistence)
```bash
MATCH (r:Router)-[:HAS_PORT]->(p:Port)-[:LINKS_TO]-()
WITH DISTINCT r,p
WITH collect(r.name+','+p.name) as ports
CALL net.trees.print(ports, true) YIELD port, tree, flag
RETURN port, tree, flag;
```