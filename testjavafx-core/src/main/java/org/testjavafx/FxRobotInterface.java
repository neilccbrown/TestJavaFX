/*
 * TestJavaFX: Testing for JavaFX applications
 * Copyright (c) Neil Brown, 2022.
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission - subsequent versions of the EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */
package org.testjavafx;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.stage.Window;
import org.testjavafx.node.NodeQuery;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The main interface with all the methods available to
 * call to fake GUI events.
 *
 * <p>See the subclasses {@link FxRobotInterfaceKeyboard} and
 * {@link FxRobotInterfaceMouse} for documentation on keyboard
 * and mouse events respectively, and the subclass {@link FxRobotInterfaceWindow} for documentation on window lookup.
 *
 * <p>Some of the methods in these classes are default and some are
 * not.  In general this is just a matter of convenience of implementation
 * as to whether they are defined here (or in superclasses) as default,
 * or left to be defined in the child FxRobot concrete class.  You should
 * ultimately only call the methods via the FxRobot implementation so it
 * does not really matter.
 *
 * <p>Any method (including it the superclasses) that returns
 * FxRobotInterface returns this object for easy chaining.
 */
public interface FxRobotInterface extends FxRobotInterfaceKeyboard<FxRobotInterface>, FxRobotInterfaceMouse<FxRobotInterface>, FxRobotInterfaceWindow<FxRobotInterface>
{
    /**
     * Sleep for the given number of milliseconds.
     *
     * <p>This method is safe to call on the FX thread,
     * although you probably don't want to block that
     * thread.
     *
     * @param milliseconds The number of milliseconds to sleep for.
     * @return This object for easy chaining.
     */
    public default FxRobotInterface sleep(int milliseconds)
    {
        try
        {
            Thread.sleep(milliseconds);
        }
        catch (InterruptedException e)
        {
            // Just cancel the sleep, I guess
        }
        return this;
    }

    /**
     * This is a convenience method to aid migration from TestFX.  Note
     * that its behaviour is different to TestFX.  This method calls
     * {@link #focusedWindow()} -- see the documentation for that method
     * to understand the difference.
     *
     * @return The return value of calling {@link #focusedWindow()}
     */
    @Deprecated
    public default Window targetWindow()
    {
        return focusedWindow();
    }

    /**
     * Start a node query using the given query.  See {@link NodeQuery} for
     * more information on node queries.
     *
     * <p>This method can be called from any thread.
     *
     * @param query The query (e.g. CSS selector) to use for the query.
     * @return The NodeQuery corresponding to executing this query in the future.
     */
    public NodeQuery lookup(String query);

    /**
     * Start a node query by (at the time of executing the query) filtering
     * all nodes in all showing windows according to the given predicate.
     *
     * <p>This method can be called from any thread.  The given predicate will
     * only be run on the FX thread, and will not have been run at the time of return
     * from this function (see {@link NodeQuery} for more information on queries).
     *
     * @param nodePredicate The test to apply to all nodes, in any windows,
     *                      anywhere down the tree.  Will be run on the FX thread.
     * @return A query that will execute the given predicatee on all nodes,
     *         in any windows, anywhere down the tree.
     */
    public NodeQuery lookup(Predicate<Node> nodePredicate);

    /**
     * Starts a NodeQuery search with the given nodes as an initial result set.
     *
     * <p>So calling from(myNodes).queryAll() is equivalent to calling
     * Set.of(myNodes).  But calling from(myNodes).lookup(".wide").queryAll() will find
     * all the nodes anywhere within myNodes with the style-class wide.
     *
     * <p>This method can be called from any thread.
     *
     * @param useAsRoots The node or nodes to use as the start of the NodeQuery search.
     *                   Calling it with an empty list will give a NodeQuery with no results.
     * @return The NodeQuery with the given nodes as the initial results.
     */
    public NodeQuery from(Node... useAsRoots);

    /**
     * Gets the centre of the given Node's bounds as
     * a coordinate on the screen.
     *
     * <p>This method is safe to call on the FX thread.  If
     * called on another thread it waits to access the bounds
     * on the FX thread, and will block if the FX thread
     * is busy.
     *
     * @param node The node to fetch the centre coordinates for.
     * @return The centre of the node as screen coordinates, according
     *         to its bounds.
     */
    public Point2D point(Node node);

    /**
     * Looks up the given node that then calculates its centre on the
     * screen using its bounds.  Equivalent to calling:
     *
     * <p><code>point(lookup(query).queryWithRetry());</code>
     *
     * <p>If no such node is found (even with the retry), null is returned.
     * If multiple nodes match the query, an arbitrary node is chosen.
     *
     * @param query The query to use to find the node.
     * @return The centre (screen position) of the first found node's bounds, or null
     *         if no such node is found.
     */
    public default Point2D point(String query)
    {
        Node node = lookup(query).queryWithRetry();
        if (node == null)
            return null;
        else
            return point(node);
    }

    /**
     * Waits until the given supplier returns true, by repeatedly retrying
     * every 100ms for 8 seconds.
     * 
     * <p>If the condition still does not return true after all the retries,
     * a {@link RuntimeException} (or some subclass) will be thrown.  A
     * return without exception indicates that the check did return true.
     *
     * @param check The check to run on the FX thread.
     * @return This, for easy chaining.
     */
    public default FxRobotInterface waitUntil(Supplier<Boolean> check)
    {
        if (!Platform.isFxApplicationThread())
        {
            for (int retries = 80; retries >= 0; retries--)
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException e)
                {
                    // Just cancel the sleep, we'll go round and retry anyway
                }
                if (FxThreadUtils.syncFx(check::get))
                    return this;
            }
        }
        else
        {
            if (check.get())
                return this;
        }
        throw new RuntimeException("waitUntil() condition was not satisfied even after retries");
    }

    /**
     * An instant query (without retrying) for whether a node is showing.
     * Useful for use with {@link #waitUntil(Supplier)}.
     * 
     * <p>Safe for using from any thread, but off the FX thread it will block
     * until it can run on the FX thread.
     *
     * @param query The query to run via {@link #lookup(String)}
     * @return True if the query finds at least one node, false if no nodes are found.
     */
    public default boolean showing(String query)
    {
        return lookup(query).query() != null;
    }

    /**
     * An instant query (without retrying) for whether a node is not showing.
     * Useful for use with {@link #waitUntil(Supplier)}.
     *
     * <p>Safe for using from any thread, but off the FX thread it will block
     * until it can run on the FX thread.
     *
     * @param query The query to run via {@link #lookup(String)}
     * @return True if the query finds no nodes, false if at least one node is found.
     */
    public default boolean notShowing(String query)
    {
        return lookup(query).query() == null;
    }
}
