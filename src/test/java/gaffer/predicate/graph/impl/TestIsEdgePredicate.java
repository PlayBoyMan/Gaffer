/**
 * Copyright 2015 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gaffer.predicate.graph.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import gaffer.graph.Edge;
import gaffer.graph.Entity;
import gaffer.graph.wrappers.GraphElement;
import gaffer.graph.wrappers.GraphElementWithStatistics;
import gaffer.statistics.SetOfStatistics;
import gaffer.statistics.impl.Count;
import org.junit.Test;

import java.io.*;
import java.util.Date;

/**
 * Basic unit test of {@link IsEdgePredicate}.
 */
public class TestIsEdgePredicate {

    @Test
    public void testWriteRead() throws IOException {
        IsEdgePredicate predicate = new IsEdgePredicate();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        predicate.write(out);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream in = new DataInputStream(bais);
        IsEdgePredicate read = new IsEdgePredicate();
        read.readFields(in);
        assertEquals(predicate, read);
    }

    @Test
    public void testAccept() throws IOException {
        IsEdgePredicate predicate = new IsEdgePredicate();
        Entity entity = new Entity("type", "value", "summaryType", "summarySubType", "visibility", new Date(100L), new Date(1000L));
        SetOfStatistics statistics = new SetOfStatistics();
        statistics.addStatistic("stat", new Count(100));
        GraphElementWithStatistics elementWithStatistics = new GraphElementWithStatistics(new GraphElement(entity), statistics);
        assertFalse(predicate.accept(elementWithStatistics));
        Edge edge = new Edge("srcType", "srcValue", "dstType", "dstValue", "summaryType", "summarySubType", true,
                "visibility", new Date(100L), new Date(1000L));
        elementWithStatistics = new GraphElementWithStatistics(new GraphElement(edge), statistics);
        assertTrue(predicate.accept(elementWithStatistics));
    }

    @Test
    public void testEquals() {
        IsEdgePredicate predicate = new IsEdgePredicate();
        IsEdgePredicate predicate2 = new IsEdgePredicate();
        assertEquals(predicate, predicate2);
        assertEquals(predicate.hashCode(), predicate2.hashCode());
    }

}
