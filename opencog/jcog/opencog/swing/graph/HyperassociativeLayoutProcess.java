/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jcog.opencog.swing.graph;

import com.syncleus.dann.graph.MutableDirectedAdjacencyGraph;
import com.syncleus.dann.math.Vector;
import edu.uci.ics.jung.graph.Hypergraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import jcog.opencog.Atom;
import jcog.opencog.swing.GraphView;
import jcog.opencog.swing.graph.GraphViewProcess;
import jcog.opencog.swing.graph.SeHHyperassociativeMap;
import jcog.spacegraph.shape.Rect;
import jcog.spacegraph.shape.TextRect;

/**
 *
 * @author seh
 */
public class HyperassociativeLayoutProcess extends GraphViewProcess {
    final int alignCycles = 1;
    final int numDimensions = 2;
    private MutableDirectedAdjacencyGraph<Atom, FoldedEdge> digraph;
    private SeHHyperassociativeMap<com.syncleus.dann.graph.Graph<Atom, FoldedEdge>, Atom> ham;

    public HyperassociativeLayoutProcess(GraphView gv) {
        super(gv);
        reset();
    }

    /**
     * Creates a <code>Graph</code> which is an edge-folded version of <code>h</code>, where
     * hyperedges are replaced by k-cliques in the output graph.
     *
     * <p>The vertices of the new graph are the same objects as the vertices of
     * <code>h</code>, and <code>a</code>
     * is connected to <code>b</code> in the new graph if the corresponding vertices
     * in <code>h</code> are connected by a hyperedge.  Thus, each hyperedge with
     * <i>k</i> vertices in <code>h</code> induces a <i>k</i>-clique in the new graph.</p>
     *
     * <p>The edges of the new graph are generated by the specified edge factory.</p>
     *
     * @param <V> vertex type
     * @param <E> input edge type
     * @param h hypergraph to be folded
     * @param graph_factory factory used to generate the output graph
     * @param edge_factory factory used to create the new edges
     * @return a copy of the input graph where hyperedges are replaced by cliques
     */
    public MutableDirectedAdjacencyGraph<Atom, FoldedEdge> foldHypergraphEdges(final Collection<Atom> vertices, final MutableDirectedAdjacencyGraph<Atom, FoldedEdge> target, final Hypergraph<Atom, Atom> h, boolean linkEdgeToMembers) {
        for (Atom v : vertices) {
            target.add(v);
        }
        for (Atom e : h.getEdges()) {
            boolean contained = true;
            for (Atom iv : mind.getIncidentVertices(e)) {
                if (!vertices.contains(iv)) {
                    contained = false;
                    break;
                }
            }
            if (!contained) {
                continue;
            }
            target.add(e);
            ArrayList<Atom> incident = new ArrayList(h.getIncidentVertices(e));
            if (incident.size() == 0) {
                continue;
            }
            if (linkEdgeToMembers) {
                for (int i = 0; i < incident.size(); i++) {
                    Atom i1 = incident.get(i);
                    if (i == 0) {
                        target.add(new FoldedEdge(e, i1, e, "("));
                    } else {
                        target.add(new FoldedEdge(incident.get(i - 1), i1, e, ""));
                    }
                }
            } else {
                final String typeString = mind.getType(e).toString();
                //Just link the edge to the first element
                for (int i = 0; i < incident.size(); i++) {
                    if (i > 0) {
                        target.add(new FoldedEdge(incident.get(i - 1), incident.get(i), e, Integer.toString(i)));
                    } else {
                        target.add(new FoldedEdge(e, incident.get(i), e, "(" + typeString));
                    }
                }
            }
        }
        return target;
    }

    @Override
    public void reset() {
        super.reset();
        digraph = foldHypergraphEdges(graphView.atomRect.keySet(), new MutableDirectedAdjacencyGraph<Atom, FoldedEdge>(), mind.getAtomSpace().graph, true);
        Collection<FoldedEdge> diEdges = digraph.getEdges();

        graphView.edgeCurve.clear();
        for (FoldedEdge fe : diEdges) {
            graphView.addEdge(fe);
        }
        
        ham = new SeHHyperassociativeMap<com.syncleus.dann.graph.Graph<Atom, FoldedEdge>, Atom>(digraph, numDimensions, true, GraphView.executor) {

            @Override
            public float getEquilibriumDistance(Atom n) {
                return graphView.param.getVertexEquilibriumDistance(n);
            }

            @Override
            public float getMeanEquilibriumDistance() {
                return graphView.param.getMeanEquilibriumDistance();
            }
        };
        for (Atom a : graphView.atomRect.keySet()) {
            Rect r = graphView.atomRect.get(a);
            final float x = r.getCenter().x();
            final float y = r.getCenter().y();
            final float z = r.getCenter().z();
            if (numDimensions >= 2) {
                ham.getCoordinates().get(a).setCoordinate(x, 1);
                ham.getCoordinates().get(a).setCoordinate(y, 2);
            }
            if (numDimensions >= 3) {
                ham.getCoordinates().get(a).setCoordinate(z, 3);
            }
            graphView.setTargetCenter(r, x, y, z);
        }
    }

    @Override
    protected void update(GraphView g) {
        if (ham == null) {
            return;
        }
        for (int i = 0; i < alignCycles; i++) {
            ham.align();
        }
        final float s = 0.2F;
        for (Entry<Atom, TextRect> i : g.atomRect.entrySet()) {
            final Vector v = ham.getCoordinates().get(i.getKey());
            if (v == null) {
                System.err.println(i + " not mapped by " + this);
            }
            TextRect tr = i.getValue();
            if (v.getDimensions() == 2) {
                float x = (float) v.getCoordinate(1) * s;
                float y = (float) v.getCoordinate(2) * s;
                //i.getValue().setCenter(x, y);
                graphView.setTargetCenter(tr, x, y, 0);
            } else if (v.getDimensions() == 3) {
                float x = (float) v.getCoordinate(1) * s;
                float y = (float) v.getCoordinate(2) * s;
                float z = (float) v.getCoordinate(3) * s;
                //i.getValue().setCenter(x, y, z);
                graphView.setTargetCenter(tr, x, y, z);
            }
        }
    }

    @Override
    public boolean isReady() {
        return accumulated > graphView.param.getLayoutUpdatePeriod();
    }
    
}