# Advanced Data Plane Analysis
In this example, we use graph algorithms from the Neo4j Graph DataScience (GDS) library to perform unsupervised Data Plane analysis.

This example assumes reachablity trees are already computed with [Java plugin](./reachability-java.md) or [Cypher procedure](./reachability-cypher.md)


## 1. Access Neo4j
Access the Neo4j console from a browser at http://localhost:7474.

Use (user:`neo4j` password:`12345`) if prompted to connect.


## 2. Find important ports in the network
1. Create Port-State graph projection
```bash
CALL gds.graph.project(
    'portAndState',
    ['Port', 'State'],
    {
        FWD_STATE: {orientation: 'REVERSE'}
    }
);
```

2. Run PageRank centrality algorithm
```bash
CALL gds.pageRank.stream('portAndState')
YIELD nodeId, score
WITH gds.util.asNode(nodeId) AS port, score
MATCH (r:Router)-[]->(port)
RETURN r.name+','+port.name AS portname, score
ORDER BY score DESC
```


## 3. Find similar routers in the network
1. Connect reachability states to EC nodes
```bash
MATCH (s:State) WHERE exists(s.flag) AND s.complement = false
MATCH (ec:EC) WHERE ec.v IN s.ecs
WITH DISTINCT s, ec
CREATE (s)-[:HAS_EC]->(ec);
MATCH (s:State) WHERE exists(s.flag) AND s.complement = true
MATCH (ec:EC) WHERE NOT ec.v IN s.ecs
WITH DISTINCT s, ec
CREATE (s)-[:HAS_EC]->(ec);
```

2. Connect routers to EC nodes
```bash
MATCH (r:Router)-[]->(p:Port)-[]->(s:State)-[:HAS_EC]->(ec:EC)
WHERE NOT (s)-[:FWD_NEXT]->()
WITH DISTINCT r, collect(DISTINCT ec) AS routerEcs
UNWIND routerEcs AS ecs
MERGE (r)-[:HAS_EC]->(ecs)
RETURN r;
```

3. Create Router-EC graph projection
```bash
CALL gds.graph.project(
    'routersAndEcs',
    ['Router', 'EC'],
    {
        HAS_EC: {}
    }
);
```

4. Run similarity algorithm
```bash
CALL gds.nodeSimilarity.stream('routersAndEcs')
YIELD node1, node2, similarity
RETURN gds.util.asNode(node1).name AS router1, gds.util.asNode(node2).name AS router2, similarity
ORDER BY similarity DESC
```