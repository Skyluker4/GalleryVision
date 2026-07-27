// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lukesimmons.galleryvision.core.model.MediaType
import com.lukesimmons.galleryvision.feature.faces.PeopleScreen
import com.lukesimmons.galleryvision.feature.library.FolderScreen
import com.lukesimmons.galleryvision.feature.library.FoldersScreen
import com.lukesimmons.galleryvision.feature.library.LibraryScreen
import com.lukesimmons.galleryvision.feature.video.VideoScreen
import com.lukesimmons.galleryvision.feature.viewer.ViewerScreen
import com.lukesimmons.galleryvision.ui.theme.GalleryVisionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GalleryVisionTheme {
                MediaPermissionGate()
            }
        }
    }
}

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasMediaAccess(context: Context): Boolean =
    mediaPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onMediaClick = { media ->
                    // Videos and animated formats play through libmpv; stills use the detection viewer.
                    val route = if (media.type == MediaType.IMAGE) "viewer" else "video"
                    navController.navigate("$route/${media.id}")
                },
                onPeopleClick = { navController.navigate("people") },
                onFoldersClick = { navController.navigate("folders") },
            )
        }
        composable("people") {
            PeopleScreen()
        }
        composable("folders") {
            FoldersScreen(onFolderClick = { id -> navController.navigate("folder/$id") })
        }
        composable(
            route = "folder/{folderId}",
            arguments = listOf(navArgument("folderId") { type = NavType.LongType }),
        ) { backStackEntry ->
            FolderScreen(
                onMediaClick = { media ->
                    val route = if (media.type == MediaType.IMAGE) "viewer" else "video"
                    navController.navigate("$route/${media.id}")
                },
            )
        }
        composable(
            route = "viewer/{mediaId}",
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
        ) { backStackEntry ->
            ViewerScreen(mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L)
        }
        composable(
            route = "video/{mediaId}",
            arguments = listOf(navArgument("mediaId") { type = NavType.LongType }),
        ) { backStackEntry ->
            VideoScreen(mediaId = backStackEntry.arguments?.getLong("mediaId") ?: 0L)
        }
    }
}

@Composable
fun MediaPermissionGate() {
    val context = LocalContext.current
    val permissions = remember { mediaPermissions() }
    var granted by remember { mutableStateOf(hasMediaAccess(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> granted = result.values.all { it } }

    if (granted) {
        AppNavHost()
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("GalleryVision needs media access to show your library.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(permissions) }) {
                Text("Grant access")
            }
        }
    }
}
