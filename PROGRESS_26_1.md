# Fabric 26.1 Migration — DONE ✅

**Status: Build kompiliert + paketiert sauber.** Output: `build/libs/fakeplayerchunkloader26.1-fabric-2.0.0.jar` (380 KB).

```
> Task :compileJava
> Task :jar
> Task :build
BUILD SUCCESSFUL
```

## Was schon migriert ist ✅

### Build-Konfiguration

- `gradle.properties`: minecraft 26.1, loader 0.19.2, loom 1.16-SNAPSHOT, fabric_api 0.145.1+26.1, java 25
- `build.gradle`: `id 'net.fabricmc.fabric-loom' version "${loom_version}"`, `implementation` statt `modImplementation`, Java 25 release-target, Toolchain bedingt
- `settings.gradle`: `rootProject.name = 'chunkloader'`
- `gradle/wrapper/gradle-wrapper.properties`: gradle-9.4.1-bin.zip
- `fabric.mod.json`: java >=25 dependency
- `chunkloader.mixins.json`: `compatibilityLevel: JAVA_25`, `PlayerEntityModelEasterEggEmoteMixin` entfernt

### Yarn → Mojmap (über alle 87 Java-Dateien angewendet)

Alle Yarn-Imports und die meisten Klassenrefs wurden umbenannt. Die wichtigsten:

- `net.minecraft.entity.*` → `net.minecraft.world.entity.*`
- `net.minecraft.server.world.ServerWorld` → `net.minecraft.server.level.ServerLevel`
- `net.minecraft.server.world.ServerChunkManager` → `ServerChunkCache`
- `net.minecraft.server.world.ChunkTicketType` → `net.minecraft.server.level.TicketType`
- `net.minecraft.text.Text` → `net.minecraft.network.chat.Component`
- `net.minecraft.util.math.{BlockPos, ChunkPos, MathHelper, Box, Direction}` → entsprechende Mojmap-Pfade
- `net.minecraft.util.Identifier` → `net.minecraft.resources.Identifier` (NICHT ResourceLocation — Mojang hat in 26.1 zurück auf `Identifier` umbenannt)
- `net.minecraft.util.Formatting` → `net.minecraft.ChatFormatting`
- `net.minecraft.client.gui.DrawContext`/`GuiGraphics` → `GuiGraphicsExtractor` (komplett neue Render-API)
- `net.minecraft.client.MinecraftClient` → `net.minecraft.client.Minecraft`
- `net.minecraft.client.gui.Click` → `net.minecraft.client.input.MouseButtonEvent`
- `net.minecraft.client.option.KeyBinding` → `net.minecraft.client.KeyMapping`
- `net.minecraft.network.PacketFlow` → `net.minecraft.network.protocol.PacketFlow`
- `net.minecraft.network.codec.PacketCodec` → `StreamCodec`
- `net.minecraft.network.PacketByteBuf` → `FriendlyByteBuf`
- `net.minecraft.network.RegistryByteBuf` → `RegistryFriendlyByteBuf`
- `net.minecraft.network.packet.CustomPayload` → `net.minecraft.network.protocol.common.custom.CustomPacketPayload`
- `net.minecraft.command.permission.*` → `net.minecraft.server.permissions.*` (auch Klassen umbenannt: `PermissionPredicate` → `PermissionSet`)
- `net.minecraft.entity.damage.DamageSource` → `net.minecraft.world.damagesource.DamageSource`
- `net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents` → `ServerLevelEvents`
- `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper` → `keymapping.v1.KeyMappingHelper`
- Mixin-JVM-Deskriptoren (slash-Form `Lnet/...`) sind ebenfalls migriert

### Methoden-Renames (Yarn → Mojmap)

- `drawText` → `text`, `drawTexture` → `blit`, `drawTooltip` → `setTooltipForNextFrame`
- `render(GuiGraphicsExtractor, ...)` → `extractRenderState(...)`, `renderBackground(...)` → `extractBackground(...)`
- `super.render(...)` → `super.extractRenderState(...)`
- `getPlayerManager` → `getPlayerList` (auf MinecraftServer)
- `getPlayerList().getPlayerList()` → `getPlayerList().getPlayers()` (Cascading-Fix)
- `getOverworld` → `overworld`, `getWorlds` → `getAllLevels`, `getSaveProperties` → `getWorldData`, `getTicks` → `getTickCount`, `getRunDirectory` → `getServerDirectory`
- `ChunkPos.toLong` → `ChunkPos.pack`, `ChunkPos.fromLong` → `ChunkPos.unpack`
- `expiryTicks` → `timeout` (TicketType ist record(long timeout, int flags))
- `formatted(Formatting…)` → `withStyle(ChatFormatting…)`
- `getEntityWorld` → `level` (auf Entity)
- `getBlockPos` → `blockPosition`, `getRegistryKey` → `dimension`, `getRegistryManager` → `registryAccess`
- `getYaw/getPitch/setYaw/setPitch` → `getYRot/getXRot/setYRot/setXRot`
- `getUuid` → `getUUID`, `getUuidAsString` → `getStringUUID`
- `refreshPositionAndAngles`/`moveTo` → `snapTo`
- `client.world` → `client.level`, `client.currentScreen` → `client.screen`, `client.textRenderer` → `client.font`
- `wasPressed` → `consumeClick`, `getBoundKeyLocalizedText` → `getTranslatedKeyMessage`
- `window.getScaledWidth/Height` → `getGuiScaledWidth/Height`
- `getChunkPos` → `chunkPosition`, `squaredDistanceTo` → `distanceToSqr`, `isSneaking` → `isShiftKeyDown`
- `dimension().getValue()` → `dimension().identifier()` (ResourceKey 26.1 Rename)
- `writeString`/`readString` → `writeUtf`/`readUtf` (FriendlyByteBuf)
- `Scoreboard.getTeam/addTeam/getTeams` → `getPlayerTeam/addPlayerTeam/getPlayerTeams`
- `BLOCK_NOTE_BLOCK_BASS/PLING` → `NOTE_BLOCK_BASS/PLING` (SoundEvents)
- `connection.sendPacket(p)` → `connection.send(p)`
- `playerList.sendToAll(p)` → `playerList.broadcastAll(p)`
- `chunkSource.addTicket(TicketType, ChunkPos, int)` → `chunkSource.addTicketWithRadius(...)`
- `chunkSource.removeTicket(TicketType, ChunkPos, int)` → `removeTicketWithRadius(...)`
- `KeyMappingHelper.registerKeyBinding` → `registerKeyMapping`
- `Font.getWidth` → `Font.width`, `Button.builder().dimensions(...)` → `bounds(...)`
- `Screen.client` (field) → `Screen.minecraft`
- `ChunkLevel.getLevelFromType` → `ChunkLevel.byStatus`
- `ChunkPos.x/z` Felder → Record-Accessoren `.x()/.z()`
- Payload: `CustomPayload.Id`/`getId()` → `CustomPacketPayload.Type`/`type()` (richtig: `type()`, NICHT `getType()`)
- Payload: Lambda `(payload, buf) ->` → `(buf, payload) ->` in `StreamCodec.of(...)`
- Payload: Feld `ID` → `TYPE` (Mojmap-Konvention)

### Manuell migriert

- `permissions/PermissionManager.java` — vollständig neu auf `net.minecraft.server.permissions.{Permission, PermissionLevel, PermissionSet}` umgeschrieben (basierend auf Forge-1.21.11-Variante)
- `client/ChunkloaderClient.java` — komplett neu geschrieben mit allen 26.1-Anpassungen, `shouldPlayEasterEggEmote` (war nur vom gestubbten Mixin benutzt) entfernt

### Stillgelegt

- `mixin/client/PlayerEntityModelEasterEggEmoteMixin.java` — gelöscht und aus `chunkloader.mixins.json` entfernt. Grund: `PlayerRenderState` wurde in 26.1 ersatzlos entfernt; das ganze Render-State-Modell wurde durch `RenderStateDataKey<T>` ersetzt. Easter-Egg-Emote braucht später eine komplette Neu-Implementierung gegen die neue API.

## Was in der Finalen Session noch dazu kam (alles erledigt)

### Hot-spot Files

- **`manager/ChunkloaderManager.java`**: explizite `tm.addTicket(long, Ticket)`/`removeTicket(long, Ticket)` Schleifen entfernt — `DistanceManager` exposed das nicht mehr. Stattdessen ausschließlich `ServerChunkCache.addTicketWithRadius`/`removeTicketWithRadius`. `world.random` → `world.getRandom()`. `world.isDay()` → `isBrightOutside()`. `PlayerTeam.getPlayerList()` → `getPlayers()`. `ClientboundSetPlayerTeamPacket.syncPlayerTeam` → `createAddOrModifyPacket`, `changePlayerTeam` → `createPlayerPacket`, `Operation.ADD` → `Action.ADD`.
- **`fakeplayer/ChunkloaderFakePlayer.java`**: Vollständig manuell migriert. Wichtigste Punkte:
  - `WaypointTransmitter.Connection` Naming-Konflikt durch Vererbung gelöst → Feld `netConnection` mit FQN `net.minecraft.network.Connection`
  - `CommonListenerCookie.createDefault` → `createInitial`
  - `placeNewPlayer` (war Yarn `onPlayerConnect`)
  - `hurtServer(ServerLevel, DamageSource, float)` (war `damage(...)`)
  - `getTabListDisplayName()` (war `getPlayerListName()`)
  - `shouldShowName()` (war `shouldRenderName()`)
  - `gameMode.changeGameModeForPlayer(GameType)` (war `changeGameMode`)
  - `Abilities.mayfly` (war `allowFlying`)
  - ClientInformation-Felder: `chatColors`, `modelCustomisation`, `mainHand`, `textFilteringEnabled`, `allowsListing`
  - `allowsServerListing()`, `sendMessage()`, `shouldDamagePlayer()` Override entfernt — existieren auf 26.1 ServerPlayer nicht mehr
- **Fabric API Method-Renames für 26.1**: `PayloadTypeRegistry.playS2C()` → `clientboundPlay()`, `playC2S()` → `serverboundPlay()`, `configurationS2C/C2S()` → `clientboundConfiguration/serverboundConfiguration()`. Wichtige Erkenntnis: `implementation` (statt `modImplementation`) ist im Fabric Loom 1.16+ für 26.1 korrekt — Loom 1.16 hat die `mod*` Configurations für Mojmap-Default abgeschafft.

### Sonstige Fixes

- `EditBox`: `getText()` → `getValue()`, `setText` → `setValue`, `setChangedListener` → `setResponder`, `setDrawsBackground` → `setBordered`, `setEditableColor` → `setTextColor`, `setPlaceholder` → `setHint`
- Screen: `addDrawableChild` → `addRenderableWidget`, `addSelectableChild` → `addWidget`, `clearChildren` → `clearWidgets`, `remove(Widget)` → `removeWidget(...)`, `shouldPause()` → `isPauseScreen()`, `close()` → `onClose()` (Screen-Override) — aber für AutoCloseable bleibt `close()`
- `Renderable.render(...)` → `Renderable.extractRenderState(...)` (an allen Call-Sites von Child-Widgets)
- `Minecraft.getNetworkHandler()` → `getConnection()`; `client.world` → `client.level`; `client.runDirectory` → `client.gameDirectory`; Yarn `textRenderer` Field-Refs → `font`
- `ClientPacketListener.getPlayerList()` → `getOnlinePlayers()`
- `Level.getEntityById(int)` → `Level.getEntity(int)`; `Level.isAir(BlockPos)` → `isEmptyBlock(BlockPos)` (BlockState bleibt `isAir()`); `Level.isInBuildLimit(BlockPos)` → `isInWorldBounds(BlockPos)`; `Level.getDayTime()` → `getDefaultClockTime()`; `Level.getDimension().minY()` → `dimensionType().minY()`
- Entity: `getDataTracker()` → `getEntityData()`; `DamageSource.getAttacker()` → `getEntity()`
- `MinecraftServer.getSavePath` → `getWorldPath`; `ServerLevel.getPlayers()` → `players()` (no-arg); `PlayerList.getPlayers()` bleibt unverändert
- `KeyMapping.updateKeysByCode()` → `setAll()`; `Options.write()` → `save()`; `Options.allKeys` → `keyMappings`
- `InputConstants.Type.KEYSYM.createFromCode` → `getOrCreate`; `KeyMapping.setBoundKey` → `setKey`; Screen-`keyPressed` Signatur: `KeyInput` → `KeyEvent`
- `BlockPos.toImmutable()` → `immutable()`; `BlockPos.Mutable` → `BlockPos.MutableBlockPos`; `BlockPos.down()` → `below()`
- `ChunkPos.getStartX/Z()` → `getMinBlockX/Z()`; `Heightmap.Type` → `Heightmap.Types`; `Level.getTopPosition` → `getHeightmapPos`
- `LevelHeightAccessor.getBottomY()` → `getMinY()`
- `MapColor.color` (field) → `MapColor.col`; `NativeImage.setColor` → `setPixel`
- `TextureManager.registerTexture/destroyTexture` → `register/release`
- `CommandSourceStack`: `sendError` → `sendFailure`, `sendFeedback` → `sendSuccess`
- `ChunkMap.getCurrentChunkHolder` → `getUpdatingChunkIfPresent`; `ServerLevel.isPositionTicking(long)` → `shouldTickBlocksAt(long)`; `ChunkHolder.getLevel/getCompletedLevel` → `getTicketLevel/getQueueLevel`
- `ServerChunkCache.chunkLoadingManager` → `chunkMap`
- `FriendlyByteBuf.writeUuid/readUuid` → `writeUUID/readUUID`; `writeEnumConstant/readEnumConstant` → `writeEnum/readEnum`
- `SynchedEntityData.getChangedEntries()` → `packDirty()`; `getAllEntries()` → `getNonDefaultValues()`
- `IntegratedPlayerListMixin`: target jetzt `canPlayerLogin(SocketAddress, NameAndId)` mit Parameter `NameAndId`
- `DummyClientConnection`: `transitionInbound` → `setupInboundProtocol`; `setInitialPacketListener` → `setListenerForServerboundHandshake`; `isOpen()/isChannelAbsent()` → `isConnected()`
- `Connection` Enum: kein `<T>` Generic mehr (war ehemals `ConnectionProtocol<T>`)
- HUD Mixin: ruft jetzt `extractRenderState(...)` auf den Static HUD-Methoden statt `render(...)`
- ChunkPos-Record: über alle Variablen (`pc`, `key`, `chunkpos`, `mp`, `chunkPosition()`...) konsequent `.x()/.z()` Accessoren
- `new ChunkPos(long packed)` → `new ChunkPos(ChunkPos.getX(packed), ChunkPos.getZ(packed))` (long-ctor weg)
- Easter-Egg-Skin/Emote-Payloads: Lambda-Reihenfolge korrekt + `writeUUID`

## Angewendete Migration-Skripte

In Reihenfolge der Anwendung (alle im Workspace-Root):

1. `_migrate-yarn-mojmap.ps1` — Phase 0/1/2/3: FQN-Imports, slash-form Mixin-Deskriptoren, Klassennamen mit Wortgrenzen, sichere Methoden-Renames
2. `_migrate-fixup.ps1` — Phase A/B/C/D: Korrekturen am ersten Pass (Identifier-Pfad, MouseButtonEvent, KeyMappingHelper, ServerLevelEvents)
3. `_migrate-render26.ps1` — Phase G: GuiGraphicsExtractor, Screen.extractRenderState, GuiGraphicsExtractor-API-Methoden, Minecraft-Field-Renames
4. `_migrate-phase-h.ps1` — Phase H: PacketFlow-Pfad, PlayerModel-Pfad, MinecraftServer-Renames, ChunkPos-Record, Payload-Type-Fixup
5. `_migrate-phase-i.ps1` — Phase I: Component.formatted→withStyle, Entity.getEntityWorld→level, Scoreboard-Renames, Sound-Konstanten, addTicketWithRadius/removeTicketWithRadius
6. `_migrate-phase-j.ps1` — Phase J: moveTo→snapTo, getTime→getGameTime, sendToAll→broadcastAll, displayClientMessage(c,b)→sendSystemMessage(c), dimension().location()→identifier()

Manuelle Folgekorrekturen über Inline-PowerShell:

- `net.minecraft.world.level.block.BlockState` Pfad gefixt (Phase-1-Bug mit Block-Prefix)
- `dimension().location()` → `dimension().identifier()`
- `writeString/readString` → `writeUtf/readUtf`
- BOM-Entfernung in allen 80 betroffenen Dateien
- `getPlayerList().getPlayerList()` → `getPlayerList().getPlayers()`
- Cascading-Fix für `connection.sendPacket(...)` → `connection.send(...)`
- `(?<=this\.)client` → `minecraft`, `dimensions(` → `bounds(`, `getWidth(` → `width(`
- `ChunkLevel.getLevelFromType(` → `ChunkLevel.byStatus(`
- `chunkPos.x` → `chunkPos.x()` (record accessor) — und `pos`, `cp`, `center`, `p` Variablen analog
- `world.random` → `world.getRandom()` (protected field access fix)
- `net.minecraft.world.chunk.Chunk` → `net.minecraft.world.level.chunk.LevelChunk`
- Payload `Id` → `Type`, `getId()` → `type()`, Lambda-Reihenfolge `(payload, buf)` → `(buf, payload)`

## Nächster Schritt

**Fabric 26.1 ist fertig.** Empfohlene Folgearbeit:

1. **Runtime-Test**: `./gradlew runClient` und `./gradlew runServer` — Build kompiliert sauber, aber Mixin-Targets/Reflection sollten zur Laufzeit verifiziert werden, vor allem die `IntegratedPlayerListMixin.canPlayerLogin`-Injection und der `FakePlayer`-Init-Pfad.
2. **Easter-Egg-Emote-Mixin neu**: gegen 26.1 `RenderStateDataKey<T>` API neu implementieren (zur Zeit stillgelegt).
3. **Forge/NeoForge 26.1 portieren**: Die Mojmap-Renames sind 1:1 übertragbar. Forge/NeoForge nutzen schon Mojmap, daher entfällt der erste 80%-Block. Hauptarbeit dort: Forge-spezifische Init-Hooks, Permission-Bridge, ggf. eigene FakePlayer-Bauweise.

## Was wir gelernt haben

- MC 26.1 ist **keine simple Update**. Mojang hat:
  - Die komplette Render-Pipeline auf "Render-State-Extraction" umgestellt (`GuiGraphicsExtractor`, `extractRenderState`)
  - Yarn-Mappings eingestellt — alle Fabric-Mods müssen auf Mojmap migrieren
  - Java auf 25 als Minimum gehoben
  - Player-Render-State ersatzlos entfernt zugunsten `RenderStateDataKey`
  - Permissions in eigenes `server/permissions/` Package verschoben mit anderer Klassenstruktur
  - `ResourceKey.location()` zu `identifier()` umbenannt
  - Chunk-Ticket-API umgebaut (`Ticket` ist record, `addTicketWithRadius`)
- Bulk-Skripte können ~80 % erledigen, der Rest ist Handarbeit
- Forge 1.21.11 ist eine **gute Mojmap-Referenz** für viele Renames, weil viele Mojmap-Methoden zwischen 1.21.11 und 26.1 unverändert sind

## Status für Forge / NeoForge

**Noch nicht angefangen.** Forge und NeoForge nutzen schon Mojmap, daher entfällt der Yarn→Mojmap-Schritt. Aber die 26.1-API-Änderungen (Render, Tickets, Permissions, ResourceKey) treffen Forge/NeoForge genauso. Plan: erst Fabric 26.1 fertig, dann das gelernte Wissen auf Forge/NeoForge übertragen — die meisten manuellen Fixes aus `ChunkloaderManager.java` und `ChunkloaderFakePlayer.java` werden 1:1 in Forge/NeoForge anwendbar sein.
