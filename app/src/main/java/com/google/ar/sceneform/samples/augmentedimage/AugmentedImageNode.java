/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.sceneform.samples.augmentedimage;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.MotionEvent;

import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.schemas.lull.Quat;

import java.util.concurrent.CompletableFuture;

/**
 * Node for rendering an augmented image. The image is framed by placing the virtual picture frame
 * at the corners of the augmented image trackable.
 */
@SuppressWarnings({"AndroidApiChecker"})
public class AugmentedImageNode extends AnchorNode implements Scene.OnTouchListener {

  private static final String TAG = "AugmentedImageNode";

  // The augmented image represented by this node.
  private AugmentedImage image;

  // Arrow and plane.  We use completable futures here to simplify
  // the error handling and asynchronous loading.  The loading is started with the
  // first construction of an instance, and then used when the image is set.
  private static CompletableFuture<ModelRenderable> arrow;
  private static CompletableFuture<ViewRenderable> popup;
  private static ModelRenderable plane;
  private static Material transparentMaterial;


  private static Context _context;

  public AugmentedImageNode(Context context) {
    _context = context;
    // Upon construction, start loading the models for the corners of the frame.
    if (arrow == null) {
      arrow = ModelRenderable.builder().setSource(context, Uri.parse("models/Pin.sfb")).build();

      popup = ViewRenderable.builder().setView(context, R.layout.solar_controls).build();

      // this is a little bit dirty :/
      // Make material for plane
      MaterialFactory.makeTransparentWithColor(context,
              new Color(1.f,1.f,1.f,0.5f)).thenAccept(material -> transparentMaterial = material
      );
    }
  }

  /**
   * Called when the AugmentedImage is detected and should be rendered. A Sceneform node tree is
   * created based on an Anchor created from the image. The corners are then positioned based on the
   * extents of the image. There is no need to worry about world coordinates since everything is
   * relative to the center of the image, which is the parent node of the corners.
   */
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  public void setImage(AugmentedImage image) {
    this.image = image;

    // If any of the models are not loaded, then recurse when all are loaded.
    if (!arrow.isDone() || !popup.isDone()) {
      CompletableFuture.allOf(arrow, popup)
              .thenAccept((Void aVoid) -> setImage(image))
              .exceptionally(
                      throwable -> {
                        Log.e(TAG, "Exception loading", throwable);
                        return null;
                      });
    }

    // Set the anchor based on the center of the image.
    setAnchor(image.createAnchor(image.getCenterPose()));

    // Create plane to get touches to annotate menu
    Node planeNode = new Node();
    planeNode.setParent(this);
    planeNode.setLocalPosition(new Vector3(0,0,0));
    planeNode.setName("menu"); // set name so we can filter on selection

    // create plane geometry if material is created
    if (transparentMaterial != null) {
      // create plane with size of image extents
      plane = ShapeFactory.makeCube(new Vector3(image.getExtentX(), 0.01f, image.getExtentZ()),
              new Vector3(0, 0, 0),
              transparentMaterial);
      planeNode.setRenderable(plane);
    }
  }

  public AugmentedImage getImage() {
    return image;
  }

  @Override
  public boolean onSceneTouch(HitTestResult hitTestResult, MotionEvent motionEvent) {
    Log.i(TAG, "onSceneTouch: "+ hitTestResult.toString());
    return false;
  }

  @Override
  public boolean onTouchEvent(HitTestResult hitTestResult, MotionEvent motionEvent) {
    // only place arrows on menu plane
    if (hitTestResult.getNode().getName() == "menu") {
      Node node = new Node();
      node.setParent(this);


      // add buffer functionality and cancel of annotation

      // TODO
      // Trying to get the arrow tip on the plane, doesn't line up well with tap
      //node.setWorldPosition(Vector3.add(hitTestResult.getPoint(), new Vector3(0f,0f,0.04f)));
      //node.setLocalPosition(new Vector3(0, 0.1f, 0)); // doesn't work? trying to raise the arrow up above menu in local frame

      // Place directly at touch location
      node.setWorldPosition(hitTestResult.getPoint());
      node.setRenderable(arrow.getNow(null));
      // scale arrow down to reasonable size
      node.setWorldScale(new Vector3(0.01f, 0.01f, 0.01f));

      // correct for arrow's initial rotation
      Quaternion z = Quaternion.axisAngle(new Vector3(0f, 0f,1f), 0f); // rotate on z axis by 45 degrees
      Quaternion y = Quaternion.axisAngle(new Vector3(1f, 0f,0), 0f); // rotate on x axis by 90 degrees
      node.setLocalRotation(Quaternion.multiply(z, y));

      // pop up prompt for menu item name/description?
      Node solarControls = new Node();
      solarControls.setParent(this);
      solarControls.setLocalScale(new Vector3(.5f, .5f, .5f));
      solarControls.setRenderable(popup.getNow(null));
      solarControls.setLocalPosition(new Vector3(0, 0.1f, -0.1f));
      //solarControls.setLocalPosition(new Vector3(0.0f, 0.25f, 0.0f));

    }
    return super.onTouchEvent(hitTestResult, motionEvent);
  }
}
