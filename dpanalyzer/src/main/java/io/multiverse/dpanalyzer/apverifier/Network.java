/*
 * Atomic Predicates Verifier
 * 
 * Copyright (c) 2013 UNIVERSITY OF TEXAS AUSTIN. All rights reserved. Developed
 * by: HONGKUN YANG and SIMON S. LAM http://www.cs.utexas.edu/users/lam/NRL/
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * with the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimers.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimers in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the UNIVERSITY OF TEXAS AUSTIN nor the names of the
 * developers may be used to endorse or promote products derived from this
 * Software without specific prior written permission.
 * 
 * 4. Any report or paper describing results derived from using any part of this
 * Software must cite the following publication of the developers: Hongkun Yang
 * and Simon S. Lam, Real-time Verification of Network Properties using Atomic
 * Predicates, IEEE/ACM Transactions on Networking, April 2016, Volume 24, No.
 * 2, pages 887-900 (first published March 2015, Digital Object Identifier:
 * 10.1109/TNET.2015.2398197).
 * 
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * CONTRIBUTORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS WITH
 * THE SOFTWARE.
 */

package io.multiverse.dpanalyzer.apverifier;

import java.io.*;
import java.util.*;

import io.multiverse.dpanalyzer.apverifier.common.*;


public class Network {
	BDDACLWrapper bddengine;
	String name;
	HashMap<String, Device> devices;

	// device|port - device|port
	HashMap<PositionTuple, HashSet<PositionTuple>> topology;
	//ArrayList<ArrayList<ACLRule>> acllib;
	APComputer fwdapc;
	
	List<String> queries = new ArrayList<String>();
	List<String> ppm = new ArrayList<String>();

	public Network(String name) throws IOException
	{
		this.name = name;
		bddengine = new BDDACLWrapper();

		devices = new HashMap<String, Device> ();
		Device.setBDDWrapper(bddengine);
		String foldername2 = "ds/st/";
		String foldername1 = "ds/stconfig/";
		String [] devicenames = {"bbra_rtr", "bbrb_rtr", "boza_rtr", "bozb_rtr", "coza_rtr", "cozb_rtr", "goza_rtr",
				"gozb_rtr", "poza_rtr", "pozb_rtr", "roza_rtr", "rozb_rtr", "soza_rtr", "sozb_rtr", "yoza_rtr", "yozb_rtr"};
		
		// init files
		queries.add("MATCH (n) DETACH DELETE n;\n");
		BuildNetwork.setQueries(queries);
		Device.setPPM(ppm);
		
		for( int i = 0; i < devicenames.length; i ++)
		{
			// generate Cypher for this device
			queries.add(String.format("CREATE (:Router {name: '%s'});\n", devicenames[i]));
			
			Device d = BuildNetwork.parseDevice(devicenames[i], foldername1 + devicenames[i] + "_config.txt"
					, foldername2 + devicenames[i] + "ap");

			//d.addSubnettoFWs();
			d.computeFWBDDs();
			d.computeACLBDDs();
			devices.put(d.name, d);
		}
		ArrayList<Integer> fwdbddary = new ArrayList<Integer> ();
		for(Device d : devices.values())
		{
			Collection<Integer> bdds = d.getfwbdds();
			for(int bdd : bdds)
			{
				fwdbddary.add(bdd);
			}
		}

		for(Device d : devices.values())
		{
			Collection<Integer> bdds = d.getinaclbdds();
			for(int bdd : bdds)
			{
				fwdbddary.add(bdd);
			}
			bdds = d.getoutaclbdds();
			for(int bdd : bdds)
			{
				fwdbddary.add(bdd);
			}
		}
		
		fwdapc = new APComputer(fwdbddary, bddengine);
		FWDAPSet.setUniverse(fwdapc.getAllAP());
		for(Device d : devices.values())
		{
			d.setaps(fwdapc);
		}

		/*
		 * topology information
		 */
		String[][] topologyArray = {
                {"bbra_rtr","te7/3","goza_rtr","te2/1"},
                {"bbra_rtr","te7/3","pozb_rtr","te3/1"},
                {"bbra_rtr","te1/3","bozb_rtr","te3/1"},
                {"bbra_rtr","te1/3","yozb_rtr","te2/1"},
                {"bbra_rtr","te1/3","roza_rtr","te2/1"},
                {"bbra_rtr","te1/4","boza_rtr","te2/1"},
                {"bbra_rtr","te1/4","rozb_rtr","te3/1"},
                {"bbra_rtr","te6/1","gozb_rtr","te3/1"},
                {"bbra_rtr","te6/1","cozb_rtr","te3/1"},
                {"bbra_rtr","te6/1","poza_rtr","te2/1"},
                {"bbra_rtr","te6/1","soza_rtr","te2/1"},
                {"bbra_rtr","te7/2","coza_rtr","te2/1"},
                {"bbra_rtr","te7/2","sozb_rtr","te3/1"},
                {"bbra_rtr","te6/3","yoza_rtr","te1/3"},
                {"bbra_rtr","te7/1","bbrb_rtr","te7/1"},
                {"bbrb_rtr","te7/4","yoza_rtr","te7/1"},
                {"bbrb_rtr","te1/1","goza_rtr","te3/1"},
                {"bbrb_rtr","te1/1","pozb_rtr","te2/1"},
                {"bbrb_rtr","te6/3","bozb_rtr","te2/1"},
                {"bbrb_rtr","te6/3","roza_rtr","te3/1"},
                {"bbrb_rtr","te6/3","yozb_rtr","te1/1"},
                {"bbrb_rtr","te1/3","boza_rtr","te3/1"},
                {"bbrb_rtr","te1/3","rozb_rtr","te2/1"},
                {"bbrb_rtr","te7/2","gozb_rtr","te2/1"},
                {"bbrb_rtr","te7/2","cozb_rtr","te2/1"},
                {"bbrb_rtr","te7/2","poza_rtr","te3/1"},
                {"bbrb_rtr","te7/2","soza_rtr","te3/1"},
                {"bbrb_rtr","te6/1","coza_rtr","te3/1"},
                {"bbrb_rtr","te6/1","sozb_rtr","te2/1"},
                {"boza_rtr","te2/3","bozb_rtr","te2/3"},
                {"coza_rtr","te2/3","cozb_rtr","te2/3"},
                {"goza_rtr","te2/3","gozb_rtr","te2/3"},
                {"poza_rtr","te2/3","pozb_rtr","te2/3"},
                {"roza_rtr","te2/3","rozb_rtr","te2/3"},
                {"soza_rtr","te2/3","sozb_rtr","te2/3"},
                {"yoza_rtr","te1/1","yozb_rtr","te1/3"},
                {"yoza_rtr","te1/2","yozb_rtr","te1/2"}
        };
		
		topology = new HashMap<PositionTuple, HashSet<PositionTuple>>();
		
		for (String[] array : topologyArray) {
			addTopology(array[0],array[1],array[2],array[3]);
			
			// generate Cypher for this link
			queries.add(String.format(
          		  "MATCH (:Router {name: '%s'})-[:HAS_PORT]->(p1:Port {name: '%s'}) "
          		+ "MATCH (:Router {name: '%s'})-[:HAS_PORT]->(p2:Port {name: '%s'}) "
          		+ "CREATE (p1)-[:LINKS_TO]->(p2);\n"
          		, array[0],array[1],array[2],array[3]));
        }
		
		// generate Cypher for PPMs
		generatePpmCypher(ppm);
	}
	
	private void addTopology(String d1, String p1, String d2, String p2)
	{
		PositionTuple pt1 = new PositionTuple(d1, p1);
		PositionTuple pt2 = new PositionTuple(d2, p2);
		// links are two way
		if(topology.containsKey(pt1))
		{
			topology.get(pt1).add(pt2);
		}else
		{
			HashSet<PositionTuple> newset = new HashSet<PositionTuple>();
			newset.add(pt2);
			topology.put(pt1, newset);
		}
		if(topology.containsKey(pt2))
		{
			topology.get(pt2).add(pt1);
		}else
		{
			HashSet<PositionTuple> newset = new HashSet<PositionTuple>();
			newset.add(pt1);
			topology.put(pt2, newset);
		}
	}
	
	private void generatePpmCypher(List<String> ppm) {		
		for(int ap : fwdapc.getAllAP()) {
			queries.add(String.format("CREATE (:EC {v: %s}) ", ap));
		}
		for (String p : ppm) {
			String[] tokens = p.split("\t");			
			String ecs = tokens[3];
			String comp = " SET x.complement = false";
			// String comp = " ";
			if (ecs.endsWith("complement")) {
	            // Remove "complement" along with the preceding space
	            ecs = ecs.substring(0, ecs.length() - 11);
	            comp = " SET x.complement = true";
	            // comp = " NOT ";
	        }
			if(tokens[0].equals( "fw")) {
				queries.add(String.format(
						"MATCH (r:Router {name: '%s'})-[:HAS_PORT|HAS_VLAN]->(i {name: '%s'}) "
					  + "CREATE (i)-[:FWD]->(x:Fwd)" +comp+ " WITH x "
					  + "MATCH (ec:EC) WHERE ec.v IN %s "
					  + "CREATE (x)-[:HAS_EC]->(ec);\n", tokens[1], tokens[2], ecs));
				/* queries.add(String.format(
						"MATCH (r:Router {name: '%s'})-[:HAS_PORT|HAS_VLAN]->(i {name: '%s'}) "
					  + "CREATE (i)-[:FWD]->(x:Fwd) WITH x "
					  + "MATCH (ec:EC) WHERE"+comp+"ec.v IN %s "
					  + "CREATE (x)-[:HAS_EC]->(ec);\n", tokens[1], tokens[2], ecs)); */
			} else if(tokens[0].equals("inacl")) {
				queries.add(String.format(
						"MATCH (r:Router {name: '%s'})-[:HAS_PORT|HAS_VLAN]->(i {name: '%s'}) "
					  + "CREATE (i)-[:INACL]->(x:InAcl)" +comp+ " WITH x "
					  + "MATCH (ec:EC) WHERE ec.v IN %s "
					  + "CREATE (x)-[:HAS_EC]->(ec)" +comp+ ";\n", tokens[1], tokens[2], ecs));
				/* queries.add(String.format(
						"MATCH (r:Router {name: '%s'})-[:HAS_PORT|HAS_VLAN]->(i {name: '%s'}) "
					  + "CREATE (i)-[:INACL]->(x:InAcl) WITH x "
					  + "MATCH (ec:EC) WHERE"+comp+"ec.v IN %s "
					  + "CREATE (x)-[:HAS_EC]->(ec);\n", tokens[1], tokens[2], ecs)); */
			} else if(tokens[0].equals("outacl")) {
				queries.add(String.format(
						"MATCH (r:Router {name: '%s'})-[:HAS_PORT|HAS_VLAN]->(i {name: '%s'}) "
				      + "CREATE (i)-[:OUTACL]->(x:OutAcl)" +comp+ " WITH x "
					  + "MATCH (ec:EC) WHERE ec.v IN %s "
					  + "CREATE (x)-[:HAS_EC]->(ec)" +comp+ ";\n", tokens[1], tokens[2], ecs));
				/* queries.add(String.format(
						"MATCH (r:Router {name: '%s'})-[:HAS_PORT|HAS_VLAN]->(i {name: '%s'}) "
				      + "CREATE (i)-[:OUTACL]->(x:OutAcl) WITH x "
					  + "MATCH (ec:EC) WHERE"+comp+"ec.v IN %s "
					  + "CREATE (x)-[:HAS_EC]->(ec);\n", tokens[1], tokens[2], ecs)); */
			}
		}
	}
	
	public List<String> getQueries() {
		return this.queries;
	}
}
