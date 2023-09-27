# Reachability with Cypher
In this example, we use a Cypher procedure to compute reachability.
The set of Equivalence Classes (EC) of reachability States are inserted as property.

The `net` Java plugin is still required for helper functions.

Use the queries below to play with network reachability.


## 1. Access Neo4j
Access the Neo4j console from a browser at http://localhost:7474.

Use (user:`neo4j` password:`12345`) if prompted to connect.


## 2. Declare reachability procedure
```bash
// declare an empty procedure to avoid recursivity error
CALL apoc.custom.declareProcedure("traverse(state_id::INT, port_id::INT) :: (ignored::INT)", 
"RETURN null AS ignored", 'read');
```
```bash
CALL apoc.custom.declareProcedure("traverse(state_id::INT, port_id::INT) :: (ignored::INT)", 
"WITH $state_id AS state_id, $port_id AS port_id 
MATCH (r:Router)-[:HAS_PORT]->(p:Port)-[]->(s:State) WHERE id(p)=port_id AND id(s)=state_id
CALL {
    WITH p, s
    OPTIONAL MATCH (p)-[:INACL]->(inacl:InAcl)-[:HAS_EC]->(ec:EC)
    WITH CASE inacl WHEN null THEN net.ap.intersect(s.ecs,s.complement,[],true) ELSE net.ap.intersect(s.ecs,s.complement,collect(ec.v),inacl.complement) END AS xfwdaps
    WITH tail(xfwdaps) as fwdaps, CASE head(xfwdaps) WHEN 0 THEN false WHEN 1 THEN true END AS fwdapsComp
    WITH DISTINCT fwdaps, fwdapsComp WHERE NOT (size(fwdaps) = 0 AND fwdapsComp = false)
    RETURN fwdaps, fwdapsComp
}
WITH r, p, s, fwdaps, fwdapsComp
CALL {
    WITH r, p, fwdaps, fwdapsComp
    MATCH (r)-[:HAS_PORT]->(pi:Port)-[:FWD]->(f:Fwd)-[:HAS_EC]->(ec:EC) WHERE pi.name <> p.name
    WITH DISTINCT pi, f, net.ap.intersect(fwdaps,fwdapsComp,collect(ec.v),f.complement) as fwdaps2
    WITH pi, fwdaps2 WHERE NOT (size(fwdaps2) = 1 AND head(fwdaps2) = 0)
    RETURN pi AS outport, fwdaps2
    UNION
    WITH r, p, fwdaps, fwdapsComp
    MATCH (r)-[:HAS_VLAN]->(vi:Vlan)-[:FWD]->(f:Fwd)-[:HAS_EC]->(ec:EC) WHERE vi.name <> p.name
    WITH DISTINCT p, vi, f, net.ap.intersect(fwdaps,fwdapsComp,collect(ec.v),f.complement) as fwdaps2
    WITH DISTINCT p, vi, fwdaps2 WHERE NOT (size(fwdaps2) = 1 AND head(fwdaps2) = 0)
    MATCH (vi)<-[:IS_VLANPORT]-(pi:Port) WHERE pi.name <> p.name
    RETURN pi as outport, fwdaps2
}
WITH DISTINCT s, outport, net.ap.unions(COLLECT(fwdaps2)) AS fwdaps2
WITH DISTINCT  s, outport, tail(fwdaps2) as fwdaps3, CASE head(fwdaps2) WHEN 0 THEN false WHEN 1 THEN true END AS fwdaps3Comp 
WITH DISTINCT  s, outport, fwdaps3, fwdaps3Comp WHERE NOT (size(fwdaps3) = 0 AND fwdaps3Comp = false)
OPTIONAL MATCH (outport)-[:OUTACL]->(outacl:OutAcl)-[:HAS_EC]->(ec:EC)
WITH DISTINCT s, outport, outacl, fwdaps3, fwdaps3Comp, CASE outacl WHEN null THEN net.ap.intersect(fwdaps3,fwdaps3Comp,[],true) ELSE net.ap.intersect(fwdaps3,fwdaps3Comp,collect(ec.v),outacl.complement) END AS xfwdaps4
WITH DISTINCT  s, outport, tail(xfwdaps4) as fwdaps4, CASE head(xfwdaps4) WHEN 0 THEN false WHEN 1 THEN true END AS fwdaps4Comp 
WITH DISTINCT  s, outport, fwdaps4, fwdaps4Comp WHERE NOT (size(fwdaps4) = 0 AND fwdaps4Comp = false)
WITH DISTINCT s, outport, fwdaps4, fwdaps4Comp
CREATE (s)-[:FWD_NEXT]->(nxt:State {ecs: fwdaps4, complement: fwdaps4Comp, originPort: s.originPort, visited: s.visited+id(outport)})<-[:FWD_STATE]-(outport)
REMOVE s.flag
SET nxt.flag = CASE (size(nxt.visited) = size(apoc.coll.toSet(nxt.visited))) WHEN false THEN CASE s.originPort = LAST(nxt.visited) WHEN true THEN 'loop' WHEN false THEN 'branch loop' END WHEN true THEN 'dead' END
WITH DISTINCT  outport, nxt WHERE nxt.flag = 'dead'
MATCH (outport)-[:LINKS_TO]-(np:Port)
REMOVE nxt.flag
WITH DISTINCT np, nxt 
CREATE (nxt)-[:FWD_NEXT]->(tf:State {ecs: nxt.ecs, complement: nxt.complement, originPort: nxt.originPort, visited: nxt.visited+id(np)})<-[:FWD_STATE]-(np)
SET tf.flag = CASE tf.originPort = id(np) WHEN true THEN 'loop' WHEN false THEN 'dead' END
WITH DISTINCT np, tf WHERE tf.flag = 'dead'
CALL custom.traverse(id(tf),id(np)) YIELD ignored
RETURN null AS ignored", 'write')
```


## 3. Compute and store reachability trees (Insert all state ECs)
```bash
// clean existing reachability states
MATCH (s:State) DETACH DELETE s;
```
```bash
MATCH (r:Router)-[:HAS_PORT]->(p:Port)-[:LINKS_TO]-()
WITH DISTINCT r, p
CREATE (p)-[:ORIG_STATE]->(s:State {originPort: id(p), ecs: [], complement:true, visited: [id(p)]})
WITH s, p
CALL custom.traverse(id(s),id(p)) YIELD ignored
RETURN null as ignored;
```


## 4. [Optional] Merge reachability trees of same port to a graph
```bash
MATCH (p:Port)-[]->(s:State)
WITH p, s.originPort AS origin, COLLECT(s) AS states
WITH p, origin, states[0] AS representativeState, states
UNWIND states[1..] AS stateToRedirect
MATCH (stateToRedirect)-[r:FWD_NEXT]->(nextState)
MERGE (representativeState)-[nr:FWD_NEXT {cmp: nextState.complement, ecs: nextState.ecs}]->(nextState)
DETACH DELETE stateToRedirect;
MATCH (p:Port)-[]->(s:State)
WITH p, s.originPort AS origin, COLLECT(s) AS states
WITH p, origin, states[0] AS representativeState, states
UNWIND states[1..] AS stateToRedirect
MATCH (prevNode)-[r:FWD_NEXT]->(stateToRedirect)
MERGE (prevNode)-[nr:FWD_NEXT {cmp: stateToRedirect.complement, ecs: stateToRedirect.ecs}]->(representativeState)
DETACH DELETE stateToRedirect;
MATCH (:State)-[f:FWD_NEXT]->(ds:State) WHERE f.ecs IS NULL
SET f.ecs = ds.ecs, f.cmp = ds.complement
REMOVE ds.complement, ds.ecs;
```

## 5. [Optional] Merge reachability graphs into a single graph
```bash
MATCH (p:Port)-[]->(s:State)
WITH p, COLLECT(s) AS states
WITH p, states[0] AS representativeState, states
UNWIND states[1..] AS stateToRedirect
MATCH (stateToRedirect)-[r:FWD_NEXT]->(nextState)
MERGE (representativeState)-[:FWD_NEXT {cmp: r.cmp, ecs: r.ecs}]->(nextState)
DETACH DELETE stateToRedirect;
MATCH (p:Port)-[]->(s:State)
WITH p, COLLECT(s) AS states
WITH p, states[0] AS representativeState, states
UNWIND states[1..] AS stateToRedirect
MATCH (prevNode)-[r:FWD_NEXT]->(stateToRedirect)
MERGE (prevNode)-[:FWD_NEXT {cmp: r.cmp, ecs: r.ecs}]->(representativeState)
DETACH DELETE stateToRedirect;
```