package com.shnupbups.extrapieces.register;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.impl.SyntaxError;
import com.shnupbups.extrapieces.ExtraPieces;
import com.shnupbups.extrapieces.core.PieceSet;
import com.shnupbups.extrapieces.core.PieceSets;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class ModConfigs {

	public static boolean generateDefaultPack = true;
	public static boolean forceUpdateDefaultPack = false;
	public static boolean everythingStonecuttable = false;
	public static boolean debugOutput = false;
	public static boolean moreDebugOutput = false;
	public static boolean dumpData = false;
	private static int setsNum = 0;
	private static int ppSetsNum = 0;
	private static int ppVpNum = 0;

	public static void init() {
		File config = new File(ExtraPieces.getConfigDirectory(), "config.json");
		try (FileReader reader = new FileReader(config)) {
			ExtraPieces.log("Loading config");
			JsonObject cfg = Jankson.builder().build().load(config);
			if (isConfigOutdated(cfg)) {
				updateConfig(cfg, config);
			}
			generateDefaultPack = cfg.get("generateDefaultPack").equals(JsonPrimitive.TRUE);
			forceUpdateDefaultPack = cfg.get("forceUpdateDefaultPack").equals(JsonPrimitive.TRUE);
			everythingStonecuttable = cfg.get("everythingStonecuttable").equals(JsonPrimitive.TRUE);
			debugOutput = cfg.get("debugOutput").equals(JsonPrimitive.TRUE);
			moreDebugOutput = cfg.get("moreDebugOutput").equals(JsonPrimitive.TRUE);
			dumpData = cfg.get("dumpData").equals(JsonPrimitive.TRUE);
		} catch (IOException e) {
			generateConfig(config);
		} catch (SyntaxError e) {
			ExtraPieces.log("SyntaxError loading config");
			ExtraPieces.log(e.getMessage());
		}
		findAndCopyPiecePacks();
	}

	public static void findAndCopyPiecePacks() {
		FabricLoader.getInstance().getAllMods().stream().map(modContainer -> {
			Path path = null;

			if (modContainer.getMetadata().containsCustomValue(ExtraPieces.mod_id + ":piecepack")) {
				ExtraPieces.debugLog("Found Piece Pack in " + modContainer.getMetadata().getName() + " (" + modContainer.getMetadata().getId() + ")");
				CustomValue pp = modContainer.getMetadata().getCustomValue(ExtraPieces.mod_id + ":piecepack");
				path = modContainer.getPath(pp.getAsString());
			}

			return path;
		}).forEach(path -> {
			if (path == null) {
				return;
			}

			if (Files.exists(path)) {
				if (path.toString().endsWith(".json")) {
					Path destination = ExtraPieces.getPiecePackDirectory().toPath().resolve(path.getFileName().toString());

					if (Files.notExists(destination)) {
						try {
							Files.createDirectories(destination.getParent());
							Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);

							ExtraPieces.debugLog("Successfully copied PiecePack " + path.getFileName() + " from a mod jar!");
						} catch (IOException e) {
							ExtraPieces.debugLog("IOException copying PiecePack " + path.getFileName() + " from a mod jar!");
						}
					} else if (isNewer(path, destination)) {
						try {
							Files.createDirectories(destination.getParent());
							Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);

							ExtraPieces.debugLog("Successfully updated PiecePack " + path.getFileName() + " from a mod jar!");
						} catch (IOException e) {
							ExtraPieces.debugLog("IOException updating PiecePack " + path.getFileName() + " from a mod jar!");
						}
					} else {
						ExtraPieces.debugLog("Piece Pack " + path.getFileName() + " already present.");
					}
				} else {
					ExtraPieces.debugLog("A mod specified a Piece Pack named " + path.getFileName() + ", but it is not a .json file! (You must include the '.json' in the name!)");
				}
			} else {
				ExtraPieces.debugLog("A mod specified a Piece Pack named " + path.getFileName() + ", but no such file existed in its jar!");
			}
		});
	}

	public static void initPiecePacks() {
		File ppDir = ExtraPieces.getPiecePackDirectory();
		File defaultPack = new File(ppDir, "default.json");
		if (ppDir.listFiles().length == 0) {
			if (generateDefaultPack) {
				ExtraPieces.log("No piece packs found, generating default!");
				generateDefaultPack(new File(ppDir, "default.json"));
			} else ExtraPieces.log("No piece packs found! Why bother having Extra Pieces installed then?");
		} else {
			if (generateDefaultPack && (!defaultPack.exists() || forceUpdateDefaultPack)) {
				generateDefaultPack(defaultPack);
			}
		}
		for (File f : ppDir.listFiles()) {
			try (FileReader reader = new FileReader(f)) {
				JsonObject pp = Jankson.builder().build().load(f);
				JsonObject sets = null;
				JsonObject vanillaPieces = null;
				String ppVer;
				if (!pp.containsKey("version")) {
					sets = pp;
					ppVer = "0.0.0";
					ExtraPieces.debugLog("Piece pack " + f.getName() + " doesn't specify a version! Please update it! Defaulting to 0.0.0");
				} else {
					if (pp.containsKey("sets")) sets = pp.getObject("sets");
					if (pp.containsKey("vanilla_pieces")) vanillaPieces = pp.getObject("vanilla_pieces");
					ppVer = pp.get(String.class, "version");
				}
				ExtraPieces.debugLog("Loading piece pack " + f.getName() + " version " + ppVer);
				if (sets != null) {
					for (Map.Entry<String, JsonElement> entry : sets.entrySet()) {
						JsonObject jsonSet = (JsonObject) entry.getValue();
						PieceSet.Builder psb = new PieceSet.Builder(entry.getKey(), jsonSet, f.getName());
						setsNum++;
						ppSetsNum++;
						ModBlocks.registerSet(psb);
					}
					ExtraPieces.debugLog("Generated " + ppSetsNum + " PieceSets from piece pack " + f.getName());
				}
				if (vanillaPieces != null) {
					for (Map.Entry<String, JsonElement> entry : vanillaPieces.entrySet()) {
						JsonObject jsonPiece = (JsonObject) entry.getValue();
						if (jsonPiece.containsKey("base") && jsonPiece.containsKey("type") && jsonPiece.containsKey("piece")) {
							Identifier base = new Identifier(jsonPiece.get(String.class, "base"));
							Identifier type = new Identifier(jsonPiece.get(String.class, "type"));
							Identifier piece = new Identifier(jsonPiece.get(String.class, "piece"));
							ppVpNum++;
							ModBlocks.registerVanillaPiece(base, type, piece);
						} else {
							ExtraPieces.debugLog("Invalid vanilla piece " + entry.getKey() + " in piece pack " + f.getName());
						}
					}
					ExtraPieces.debugLog("Added " + ppVpNum + " vanilla pieces from piece pack " + f.getName());
				}

			} catch (IOException e) {
				ExtraPieces.debugLog("IOException loading piece pack " + f.getName());
				ExtraPieces.debugLog(e.getMessage());
			} catch (SyntaxError e) {
				ExtraPieces.debugLog("SyntaxError loading piece pack " + f.getName());
				ExtraPieces.debugLog(e.getCompleteMessage());
			}
			ppSetsNum = 0;
			ppVpNum = 0;
		}
		ExtraPieces.debugLog("Generated " + setsNum + " PieceSets!");
	}

	public static void generateConfig(File config) {
		updateConfig(new JsonObject(), config);
	}

	public static void generateDefaultPack(File pack) {
		if (pack.exists()) pack.delete();
		ModBlocks.generateDefaultSets();
		try (FileWriter writer = new FileWriter(pack)) {
			JsonObject pp = new JsonObject();
			pp.put("version", new JsonPrimitive(ExtraPieces.piece_pack_version));
			JsonObject sets = new JsonObject();
			for (PieceSet set : PieceSets.defaults.values()) {
				sets.put(set.getName(), set.toJson());
			}
			pp.put("sets", sets);
			writer.write(pp.toJson(false, true));
		} catch (IOException e) {
			ExtraPieces.log("Failed to write to default piece pack!");
			ExtraPieces.log(e.getMessage());
		}
	}

	public static boolean isConfigOutdated(JsonObject cfg) {
		if (!cfg.containsKey("generateDefaultPack")) return true;
		if (!cfg.containsKey("forceUpdateDefaultPack")) return true;
		if (!cfg.containsKey("everythingStonecuttable")) return true;
		if (!cfg.containsKey("debugOutput")) return true;
		if (!cfg.containsKey("moreDebugOutput")) return true;
		if (!cfg.containsKey("dumpData")) return true;
		return false;
	}

	public static void updateConfig(JsonObject cfg, File config) {
		if (!cfg.containsKey("generateDefaultPack"))
			cfg.put("generateDefaultPack", new JsonPrimitive(generateDefaultPack));
		if (!cfg.containsKey("forceUpdateDefaultPack"))
			cfg.put("forceUpdateDefaultPack", new JsonPrimitive(forceUpdateDefaultPack));
		if (!cfg.containsKey("everythingStonecuttable"))
			cfg.put("everythingStonecuttable", new JsonPrimitive(everythingStonecuttable));
		if (!cfg.containsKey("debugOutput"))
			cfg.put("debugOutput", new JsonPrimitive(debugOutput));
		if (!cfg.containsKey("moreDebugOutput"))
			cfg.put("moreDebugOutput", new JsonPrimitive(moreDebugOutput));
		if (!cfg.containsKey("dumpData"))
			cfg.put("dumpData", new JsonPrimitive(dumpData));
		if (config.exists()) config.delete();
		try (FileWriter writer = new FileWriter(config)) {
			writer.write(cfg.toJson(false, true));
		} catch (IOException e) {
			ExtraPieces.log("Failed to write to config!");
			ExtraPieces.log(e.getMessage());
		}
	}

	public static boolean isNewer(Path toCheck, Path toReplace) {
		try {
			Path tempDir = Files.createTempDirectory("piecepacks");
			File toCheckFile = Files.copy(toCheck, tempDir.resolve(toCheck.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING).toFile();
			File toReplaceFile = toReplace.toFile();
			JsonObject ppC = Jankson.builder().build().load(toCheckFile);
			JsonObject ppR = Jankson.builder().build().load(toReplaceFile);
			Version cVer;
			Version rVer;
			if (ppC.containsKey("version")) {
				cVer = new Version(ppC.get(String.class, "version"));
			} else cVer = new Version();
			if (ppR.containsKey("version")) {
				rVer = new Version(ppR.get(String.class, "version"));
			} else rVer = new Version();
			return cVer.compareTo(rVer) == 1;
		} catch (Exception e) {
			ExtraPieces.log("Failed to check if Piece Pack " + toCheck.getFileName() + " needed updating:");
			e.printStackTrace();

			return false;
		}
	}

	public static class Version implements Comparable<Version> {

		private String version;

		public Version(String version) {
			if (version == null)
				throw new IllegalArgumentException("Version can not be null");
			if (!version.matches("[0-9]+(\\.[0-9]+)*"))
				throw new IllegalArgumentException("Invalid version format");
			this.version = version;
		}

		public Version() {
			this("0.0.0");
		}

		public final String get() {
			return this.version;
		}

		@Override
		public int compareTo(Version that) {
			if (that == null)
				return 1;
			String[] thisParts = this.get().split("\\.");
			String[] thatParts = that.get().split("\\.");
			int length = Math.max(thisParts.length, thatParts.length);
			for (int i = 0; i < length; i++) {
				int thisPart = i < thisParts.length ?
						Integer.parseInt(thisParts[i]) : 0;
				int thatPart = i < thatParts.length ?
						Integer.parseInt(thatParts[i]) : 0;
				if (thisPart < thatPart)
					return -1;
				if (thisPart > thatPart)
					return 1;
			}
			return 0;
		}

		@Override
		public boolean equals(Object that) {
			if (this == that)
				return true;
			if (that == null)
				return false;
			if (this.getClass() != that.getClass())
				return false;
			return this.compareTo((Version) that) == 0;
		}

		@Override
		public String toString() {
			return version;
		}
	}
}
