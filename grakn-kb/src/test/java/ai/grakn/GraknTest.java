/*
 * GRAKN.AI - THE KNOWLEDGE GRAPH
 * Copyright (C) 2018 Grakn Labs Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.grakn;

import ai.grakn.factory.EmbeddedGraknSession;
import ai.grakn.kb.internal.EmbeddedGraknTx;
import ai.grakn.kb.internal.GraknTxTinker;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class GraknTest {

    @Test
    public void testInMemory(){
        assertThat(Grakn.session(Grakn.IN_MEMORY, "test").transaction(GraknTxType.WRITE), instanceOf(GraknTxTinker.class));
    }

    @Test
    public void testInMemorySingleton(){
        GraknTx test1 = Grakn.session(Grakn.IN_MEMORY, "test1").transaction(GraknTxType.WRITE);
        test1.close();
        GraknTx test11 = Grakn.session(Grakn.IN_MEMORY, "test1").transaction(GraknTxType.WRITE);
        GraknTx test2 = Grakn.session(Grakn.IN_MEMORY, "test2").transaction(GraknTxType.WRITE);

        assertEquals(test1, test11);
        assertNotEquals(test1, test2);
    }

    @Test
    public void testInMemoryClear(){
        GraknTx graph = Grakn.session(Grakn.IN_MEMORY, "default").transaction(GraknTxType.WRITE);
        graph.admin().delete();
        graph = Grakn.session(Grakn.IN_MEMORY, "default").transaction(GraknTxType.WRITE);
        graph.putEntityType("A thing");
        assertNotNull(graph.getEntityType("A thing"));
    }

    @Test
    public void testComputer(){
        GraknComputer computer = ((EmbeddedGraknSession) Grakn.session(Grakn.IN_MEMORY, "bob")).getGraphComputer();
        assertThat(computer, instanceOf(GraknComputer.class));
    }

    @Test
    public void testSingletonBetweenBatchAndNormalInMemory(){
        String keyspace = "test1";
        EmbeddedGraknTx<?> graph = (EmbeddedGraknTx<?>) Grakn.session(Grakn.IN_MEMORY, keyspace).transaction(GraknTxType.WRITE);
        Graph tinkerGraph = graph.getTinkerPopGraph();
        graph.close();
        EmbeddedGraknTx<?> batchGraph = (EmbeddedGraknTx<?>) Grakn.session(Grakn.IN_MEMORY, keyspace).transaction(GraknTxType.BATCH);

        assertNotEquals(graph, batchGraph);
        assertEquals(tinkerGraph, batchGraph.getTinkerPopGraph());

        graph.close();
        batchGraph.close();
    }

    @Test
    public void whenGettingSessionForSameKeyspaceFromMultipleThreads_EnsureSingleSessionIsReturned() throws ExecutionException, InterruptedException {
        Keyspace keyspace = Keyspace.of("myspecialkeyspace");
        Set<Future<?>> futures = ConcurrentHashMap.newKeySet();
        Set<GraknSession> sessions = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(10);

        for(int i =0; i < 50; i ++){
            futures.add(pool.submit(() -> sessions.add(Grakn.session(Grakn.IN_MEMORY, keyspace))));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        assertEquals(1, sessions.size());
    }
}