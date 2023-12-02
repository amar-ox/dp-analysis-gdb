# Common Data Plane Analysis
In this example, we use Cypher queries to perform common Data Plane analysis.

This example assumes reachablity trees are already computed with [Java plugin](./reachability-java.md) or [Cypher procedure](./reachability-cypher.md)


## 1. Access Neo4j
Access the Neo4j console from a browser at http://localhost:7474.

Use (user:`neo4j` password:`12345`) if prompted to connect.


## 2. Show topology
```bash
MATCH (r:Router)-[:HAS_PORT]->(p:Port)-[:LINKS_TO]->(pp:Port)<-[:HAS_PORT]-(rr:Router)
CALL apoc.create.vRelationship(r,'LINK',{},rr) YIELD rel
RETURN r, rel, rr;
```

## 3. Show reachability trees
```bash
MATCH (s:State)<-[:ORIG_STATE]-()
MATCH path = (s)-[:FWD_NEXT*]->(e:State) WHERE NOT (e)-[:FWD_NEXT]->()
UNWIND nodes(path) AS n
MATCH (n)<-[]-(p:Port)<-[:HAS_PORT]-(r:Router) 
WITH path, collect({state: n, router:r, port:p}) as ports, e
RETURN REDUCE(s = '', pr IN ports | s + ' ' + pr.router.name + ',' + pr.port.name) AS paths, e.flag
```

## 4. Find possible loops from a port
```bash
MATCH (s:State)<-[:ORIG_STATE]-(:Port {name: 'te7/3'})-[]-(:Router {name: 'bbra_rtr'})
MATCH path = (s)-[:FWD_NEXT*]->(e:State) WHERE e.flag ENDS WITH 'loop'
RETURN path
```

## 5. Count paths per port
```bash
MATCH (r:Router)-[:HAS_PORT]->(p:Port)-[:LINKS_TO]-()
WITH DISTINCT r, p
MATCH path = ()-[:ORIG_STATE]->(os:State)-[:FWD_NEXT*]->(:State)<-[:FWD_STATE]-(p)
RETURN r.name+','+p.name AS portname, count(path)
```

## 6. Verify waypoint
```bash
MATCH (:Router {name:'goza_rtr'})-[]->(:Port{name:'te2/1'})-[:ORIG_STATE]->(os:State)
MATCH (fs:State)<-[]-(dp:Port)<-[]-(dr:Router) WHERE NOT (fs)-[:FWD_NEXT]->() AND dr.name+','+dp.name IN ["yoza_rtr,te7/1", "bbrb_rtr,te6/2", "bbrb_rtr,te7/2", "bbrb_rtr,te7/3", "bbrb_rtr,te1/2", "bbrb_rtr,te1/1", "bbrb_rtr,gi4/8", "bbrb_rtr,te6/4", "bbrb_rtr,gi4/3", "bbrb_rtr,gi5/1", "roza_rtr,te3/1", "yozb_rtr,te1/4", "bbrb_rtr,te6/3", "boza_rtr,te3/1", "rozb_rtr,te2/1", "bbrb_rtr,te1/4", "bozb_rtr,te2/1", "bbrb_rtr,te7/4", "bbrb_rtr,te6/1", "gozb_rtr,te2/1", "poza_rtr,te3/1"] 
CALL apoc.path.spanningTree(os, {endNodes:[fs], labelFilter:'State'})
YIELD path
WITH path WHERE NONE(s IN nodes(path) WHERE (s)<-[:FWD_STATE]-(:Port)<-[]-(:Router {name: 'bbra_rtr'}))
RETURN path
```