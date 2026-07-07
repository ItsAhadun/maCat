package com.ahad.macat.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * In-app camera (CameraX) that always starts with the BACK lens — the system camera
 * intent left the lens choice to the camera app, which kept opening the selfie camera.
 *
 * [onPhotoCaptured] is called with a file Uri for every shot; the screen stays open so
 * bulk mode can capture repeatedly. Pass [captureCount] to show how many were taken.
 */
@Composable
fun CameraCaptureScreen(
  newCaptureFile: () -> File,
  onPhotoCaptured: (Uri) -> Unit,
  onClose: () -> Unit,
  captureCount: Int? = null,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  BackHandler(onBack = onClose)

  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var permissionDenied by remember { mutableStateOf(false) }
  val requestPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
      permissionDenied = !granted
    }
  LaunchedEffect(Unit) {
    if (!hasPermission) requestPermission.launch(Manifest.permission.CAMERA)
  }

  Box(Modifier.fillMaxSize().background(Color.Black)) {
    if (hasPermission) {
      val previewView = remember { PreviewView(context) }
      val imageCapture = remember {
        ImageCapture.Builder()
          .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
          .build()
      }
      var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

      LaunchedEffect(lensFacing) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context)
        val preview =
          Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
          lifecycleOwner,
          CameraSelector.Builder().requireLensFacing(lensFacing).build(),
          preview,
          imageCapture,
        )
      }

      AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

      // Top bar: close + flip
      Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        IconButton(onClick = onClose) {
          Icon(Icons.Default.Close, contentDescription = "Close camera", tint = Color.White)
        }
        IconButton(
          onClick = {
            lensFacing =
              if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
              else CameraSelector.LENS_FACING_BACK
          }
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Flip camera", tint = Color.White)
        }
      }

      // Bottom bar: counter + shutter + done
      Column(
        modifier =
          Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (captureCount != null && captureCount > 0) {
          Text(
            "$captureCount photo${if (captureCount == 1) "" else "s"} taken",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Button(
            onClick = {
              val file = newCaptureFile()
              val output = ImageCapture.OutputFileOptions.Builder(file).build()
              imageCapture.takePicture(
                output,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                  override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onPhotoCaptured(Uri.fromFile(file))
                  }

                  override fun onError(exception: ImageCaptureException) {
                    // keep the camera open; the user can simply try again
                  }
                },
              )
            },
            modifier = Modifier.padding(8.dp).size(72.dp),
            shape = CircleShape,
            colors =
              ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
          ) {}
          if (captureCount != null) {
            Button(onClick = onClose, modifier = Modifier.padding(start = 16.dp)) { Text("Done") }
          }
        }
      }
    } else {
      Column(
        modifier = Modifier.align(Alignment.Center).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          if (permissionDenied) "Camera permission was denied.\nAllow it to take photos."
          else "Camera permission is required.",
          color = Color.White,
          style = MaterialTheme.typography.bodyLarge,
        )
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) {
            Text("Grant permission")
          }
          Button(onClick = onClose) { Text("Close") }
        }
      }
    }
  }
}

