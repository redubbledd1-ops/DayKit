# Sound Package

Package voor het beheren van custom alarm sounds met reactive state management.

## Componenten

### Sound.kt
Domain model voor custom alarm sounds.

```kotlin
data class Sound(
    val id: Long,
    val name: String,
    val filePath: String,
    val duration: Long
)
```

### SoundRepository.kt
Repository voor sound management met StateFlow.

**Features:**
- Leest sounds uit `context.filesDir/alarm_sounds/custom`
- Observeert database changes via Room Flow
- Exposeert StateFlow voor reactive UI updates
- Singleton pattern voor app-wide toegang

**Gebruik:**
```kotlin
val repository = SoundRepository.getInstance(context)

// Observeer sounds
repository.sounds.collect { sounds ->
    // UI update
}

// Add sound
val result = repository.addSound(uri)

// Delete sound
repository.deleteSound(sound)
```

### SoundViewModel.kt
ViewModel voor UI state management.

**Features:**
- Exposeert repository StateFlows
- Beheert UI-specifieke state
- ViewModelScope voor lifecycle-aware operations
- Factory voor dependency injection

**Gebruik:**
```kotlin
val viewModel: SoundViewModel = viewModel(
    factory = SoundViewModel.Factory(context)
)

// In Composable
val sounds by viewModel.sounds.collectAsState()
val isLoading by viewModel.isLoading.collectAsState()

// Add sound
viewModel.addSound(uri)

// Delete sound
viewModel.deleteSound(sound)
```

## Architectuur

```
Database → Repository → ViewModel → UI
  (Room)   (StateFlow)  (Compose)
```

## Realtime Updates

Alle changes in database worden automatisch gepropageerd naar UI via StateFlow:
1. Database change
2. Room Flow emits
3. Repository updates StateFlow
4. UI collectAsState() triggers recomposition

## Gebruik in UI

```kotlin
@Composable
fun MyScreen() {
    val viewModel: SoundViewModel = viewModel(
        factory = SoundViewModel.Factory(LocalContext.current)
    )
    
    val sounds by viewModel.sounds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    LazyColumn {
        items(sounds) { sound ->
            SoundItem(sound = sound)
        }
    }
}
```

## Dependencies

```kotlin
// Lifecycle & ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

// Room (already in project)
implementation("androidx.room:room-ktx:2.6.1")
```

## Testing

```kotlin
@Test
fun `repository emits sounds from database`() = runTest {
    val repository = SoundRepository.getInstance(context)
    
    repository.sounds.test {
        val sounds = awaitItem()
        assertTrue(sounds.isNotEmpty())
    }
}
```

## Zie ook

- `SOUND_REPOSITORY_CHANGELOG.md` - Volledige changelog
- `IMPLEMENTATION_SUMMARY.md` - Implementatie samenvatting
- `SCREENSHOT_GUIDE.md` - Screenshot instructies
