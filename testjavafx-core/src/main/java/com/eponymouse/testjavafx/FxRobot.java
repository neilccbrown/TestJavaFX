/*
 * TestJavaFX: Testing for JavaFX applications
 * Copyright (c) TestFX contributors 2013-2019 and Neil Brown, 2022.
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
package com.eponymouse.testjavafx;

import com.eponymouse.testjavafx.node.NodeQuery;
import com.google.common.collect.ImmutableList;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public class FxRobot implements FxRobotInterface
{
    private final Robot actualRobot = FxThreadUtils.syncFx(Robot::new);
    private final Set<KeyCode> pressedKeys = new HashSet<>();
    
    @Override
    public FxRobotInterface push(KeyCode... keyCodes)
    {
        ImmutableList<KeyCode> order = ImmutableList.copyOf(keyCodes);
        order.forEach(c -> FxThreadUtils.asyncFx(() -> actualRobot.keyPress(c)));
        order.reverse().forEach(c -> FxThreadUtils.asyncFx(() -> actualRobot.keyRelease(c)));
        FxThreadUtils.asyncFx(() -> pressedKeys.removeAll(order));
        FxThreadUtils.waitForFxEvents();
        return this;
    }

    @Override
    public FxRobotInterface press(KeyCode... keyCodes)
    {
        ImmutableList<KeyCode> order = ImmutableList.copyOf(keyCodes);
        order.forEach(c -> FxThreadUtils.asyncFx(() -> actualRobot.keyPress(c)));
        FxThreadUtils.asyncFx(() -> pressedKeys.addAll(order));
        FxThreadUtils.waitForFxEvents();
        return this;
    }

    @Override
    public FxRobotInterface release(KeyCode... keyCodes)
    {
        ImmutableList<KeyCode> order = keyCodes.length == 0 ? FxThreadUtils.syncFx(() -> ImmutableList.copyOf(pressedKeys)) : ImmutableList.copyOf(keyCodes);
        order.forEach(c -> FxThreadUtils.asyncFx(() -> actualRobot.keyRelease(c)));
        FxThreadUtils.asyncFx(() -> pressedKeys.removeAll(order));
        FxThreadUtils.waitForFxEvents();
        return this;
    }

    // Taken from TestFX:
    @Override
    public FxRobotInterface write(String text, int millisecondDelay)
    {
        Window w = focusedWindow();
        Scene scene = w.getScene();
        text.chars().forEach(c -> {
            FxThreadUtils.asyncFx(() -> Event.fireEvent(getEventTarget(scene), createKeyEvent(KeyEvent.KEY_PRESSED, KeyCode.UNDEFINED, "")));
            FxThreadUtils.asyncFx(() -> Event.fireEvent(getEventTarget(scene), createKeyEvent(KeyEvent.KEY_TYPED, KeyCode.UNDEFINED, Character.toString(c))));
            FxThreadUtils.asyncFx(() -> Event.fireEvent(getEventTarget(scene), createKeyEvent(KeyEvent.KEY_RELEASED, KeyCode.UNDEFINED, "")));
            sleep(millisecondDelay);
        });
        FxThreadUtils.waitForFxEvents();
        return this;
    }

    public FxRobotInterface sleep(int millisecondDelay)
    {
        try
        {
            Thread.sleep(millisecondDelay);
        }
        catch (InterruptedException e)
        {
        }
        return this;
    }

    // Taken from TestFX:
    private KeyEvent createKeyEvent(EventType<KeyEvent> eventType, KeyCode keyCode, String character) {
        boolean pressed = eventType == KeyEvent.KEY_PRESSED;
        boolean isShiftDown = false;
        boolean isControlDown = false;
        boolean isAltDown = false;
        boolean isMetaDown = false;
        if (keyCode == KeyCode.SHIFT) {
            isShiftDown = pressed;
        }
        if (keyCode == KeyCode.CONTROL) {
            isControlDown = pressed;
        }
        if (keyCode == KeyCode.ALT) {
            isAltDown = pressed;
        }
        if (keyCode == KeyCode.META) {
            isMetaDown = pressed;
        }

        boolean typed = eventType == KeyEvent.KEY_TYPED;
        String keyChar = typed ? character : KeyEvent.CHAR_UNDEFINED;
        String keyText = typed ? "" : keyCode.getName();
        return new KeyEvent(eventType, keyChar, keyText, keyCode, isShiftDown, isControlDown, isAltDown, isMetaDown);
    }
    
    // Taken from TestFX:
    private EventTarget getEventTarget(Scene scene) {
        return scene.getFocusOwner() != null ? scene.getFocusOwner() : scene;
    }
    
        
    @Override
    public Window targetWindow()
    {
        return focusedWindow();
    }

    @Override
    public List<Window> listWindows()
    {
        return FxThreadUtils.syncFx(() -> ImmutableList.copyOf(Window.getWindows()));
    }

    private Window focusedWindow()
    {
        return FxThreadUtils.syncFx(() -> Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null));
    }

    @Override
    public NodeQuery lookup(String query)
    {
        Node root = FxThreadUtils.syncFx(() -> focusedWindow().getScene().getRoot());
        return from(root).lookup(query);
    }

    @Override
    public NodeQuery lookup(Predicate<Node> nodePredicate)
    {
        return new NodeQueryImpl(() -> ImmutableList.of(focusedWindow().getScene().getRoot())).lookup(nodePredicate);
    }

    public NodeQuery from(Node... roots)
    {
        ImmutableList<Node> allRoots = ImmutableList.copyOf(roots);
        return new NodeQueryImpl(() -> allRoots);
    }

    @Override
    public FxRobotInterface clickOn(String query, MouseButton... mouseButtons)
    {
        Node node = lookup(query).queryWithRetry();
        clickOn(FxThreadUtils.syncFx(() -> node.localToScreen(new Point2D(node.getBoundsInLocal().getCenterX(), node.getBoundsInLocal().getCenterY()))), mouseButtons);
        return this;
    }

    @Override
    public FxRobotInterface clickOn(MouseButton... mouseButtons)
    {
        MouseButton[] actualButtons = mouseButtons.length == 0 ? new MouseButton[] {MouseButton.PRIMARY} : mouseButtons;
        FxThreadUtils.syncFx(() -> actualRobot.mouseClick(actualButtons));
        FxThreadUtils.waitForFxEvents();
        return this;
    }

    @Override
    public FxRobotInterface moveTo(Point2D screenPosition, Motion motion)
    {
        if (motion == Motion.STRAIGHT_LINE && !Platform.isFxApplicationThread())
        {
            CompletableFuture<Boolean> f = new CompletableFuture<>();
            Platform.runLater(() -> {
                Point2D curPos = actualRobot.getMousePosition();
                double pixelsPerSecond = 500;
                double seconds = screenPosition.distance(curPos) / pixelsPerSecond;
                Timeline t = new Timeline();

                for (double s = 0; s < seconds; s += 1.0/32.0)
                {
                    double proportion = s / seconds;
                    t.getKeyFrames().add(new KeyFrame(Duration.seconds(s), e -> {
                        actualRobot.mouseMove(
                         curPos.getX() + (screenPosition.getX() - curPos.getX()) * proportion,
                         curPos.getY() + (screenPosition.getY() - curPos.getY()) * proportion
                        );
                    }));
                }
                        
                t.setOnFinished(e -> {
                    // Make sure to always end on exact position:
                    actualRobot.mouseMove(screenPosition);
                    f.complete(true);
                });
                t.play();
            });
            try
            {
                f.get(5, TimeUnit.SECONDS);
            }
            catch (ExecutionException | InterruptedException | TimeoutException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            FxThreadUtils.syncFx(() -> actualRobot.mouseMove(screenPosition));
        }
        FxThreadUtils.waitForFxEvents();
        return this;
    }

    @Override
    public FxRobotInterface moveTo(String query, Motion motion)
    {
        return moveTo(point(query), motion);
    }

    @Override
    public Point2D point(Node node)
    {
        return FxThreadUtils.syncFx(() -> {
            Bounds screenBounds = node.localToScreen(node.getBoundsInLocal());
            return new Point2D(screenBounds.getCenterX(), screenBounds.getCenterY());
        });
    }
}
