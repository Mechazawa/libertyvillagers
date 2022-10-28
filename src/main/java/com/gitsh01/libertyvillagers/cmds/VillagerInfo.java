package com.gitsh01.libertyvillagers.cmds;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.literal;

public class VillagerInfo {

    final static String BLANK_COORDS = "                 ";

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("villagerinfo").executes(context -> {
                    processVillagerInfo(context);
                    return 1;
                })));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> dispatcher.register(literal("vi").executes(context -> {
                    processVillagerInfo(context);
                    return 1;
                })));
    }

    public static void processVillagerInfo(CommandContext<ServerCommandSource> command) throws CommandSyntaxException {
        ServerCommandSource source = command.getSource();
        ServerPlayerEntity player = source.getPlayer();
        ServerWorld serverWorld = source.getWorld();

        float maxDistance = 50;
        float tickDelta = 0;
        Vec3d vec3d = player.getCameraPosVec(tickDelta);
        Vec3d vec3d2 = player.getRotationVec(tickDelta);
        Vec3d vec3d3 = vec3d.add(vec3d2.x * maxDistance, vec3d2.y * maxDistance, vec3d2.z * maxDistance);
        HitResult hit = serverWorld.raycast(
                new RaycastContext(vec3d, vec3d3, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
                        player));

        // Found a block between us and the max distance, update the max distance for the entity check.
        if (hit.getType() != HitResult.Type.MISS) {
            vec3d3 = hit.getPos();
        }

        HitResult hitResult2;
        // Look for an entity between us and the block.
        if ((hitResult2 = ProjectileUtil.getEntityCollision(serverWorld, player, vec3d2, vec3d3,
                player.getBoundingBox().stretch(player.getVelocity()).expand(maxDistance),
                entity -> entity.isAlive())) != null) {
            hit = hitResult2;
        }

        List<Text> lines = null;
        switch (hit.getType()) {
            case MISS:
                break;
            case BLOCK:
                BlockHitResult blockHit = (BlockHitResult) hit;
                BlockPos blockPos = blockHit.getBlockPos();
                BlockState blockState = serverWorld.getBlockState(blockPos);
                lines = getBlockInfo(blockPos, blockState);
                break;
            case ENTITY:
                EntityHitResult entityHit = (EntityHitResult) hit;
                Entity entity = entityHit.getEntity();
                lines = getEntityInfo(entity);
                break;
        }

        if (lines != null) {
            for (Text line : lines) {
                player.sendMessage(line);
            }
        }
    }


    public static List<Text> getEntityInfo(Entity entity) {
        List<Text> lines = new ArrayList();
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.title"));
        Text name = entity.getDisplayName();
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.name", name));

        if (!(entity instanceof VillagerEntity)) {
            return lines;
        }

        String occupation = ((VillagerEntity) entity).getVillagerData().getProfession().id();
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.occupation", occupation));

        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isIntegratedServerRunning()) {
            lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.needsServer"));
            return lines;
        }

        ServerWorld world = client.getServer().getWorld(client.world.getRegistryKey());

        // Client-side villagers don't have memories.
        VillagerEntity villager = (VillagerEntity) world.getEntity(entity.getUuid());
        Optional<GlobalPos> home = villager.getBrain().getOptionalMemory(MemoryModuleType.HOME);
        String homeCoords = home.isPresent() ? home.get().getPos().toShortString() : BLANK_COORDS;
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.home", homeCoords));

        Optional<GlobalPos> jobSite = villager.getBrain().getOptionalMemory(MemoryModuleType.JOB_SITE);
        String jobSiteCoords = jobSite.isPresent() ? jobSite.get().getPos().toShortString() : BLANK_COORDS;
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.jobSite", jobSiteCoords));

        Optional<GlobalPos> potentialJobSite =
                villager.getBrain().getOptionalMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        String potentialJobSiteCoords =
                potentialJobSite.isPresent() ? potentialJobSite.get().getPos().toShortString() : BLANK_COORDS;
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.potentialJobSite", potentialJobSiteCoords));

        Optional<GlobalPos> meetingPoint = villager.getBrain().getOptionalMemory(MemoryModuleType.MEETING_POINT);
        String meetingPointCoords =
                meetingPoint.isPresent() ? meetingPoint.get().getPos().toShortString() : BLANK_COORDS;
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.meetingPoint", meetingPointCoords));

        Optional<WalkTarget> walkTarget = villager.getBrain().getOptionalMemory(MemoryModuleType.WALK_TARGET);
        String walkTargetCoords =
                walkTarget.isPresent() ? walkTarget.get().getLookTarget().getBlockPos().toShortString() : BLANK_COORDS;
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.walkTarget", walkTargetCoords));

        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.inventory"));

        if (villager.getInventory().isEmpty()) {
            lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.empty"));
        } else {
            for (ItemStack stack : villager.getInventory().stacks) {
                if (!stack.isEmpty()) {
                    lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.inventoryLine", stack.getCount(),
                            stack.getName()));
                }
            }
        }

        return lines;
    }

    public static List<Text> getBlockInfo(BlockPos blockPos, BlockState blockState) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.title"));

        Block block = blockState.getBlock();
        Text name = block.getName();
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.name", name));

        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isIntegratedServerRunning()) {
            lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.needsServer"));
            return lines;
        }

        ServerWorld world = client.getServer().getWorld(client.world.getRegistryKey());
        return getBlockInfo(world, blockPos, blockState);
    }

    public static List<Text> getBlockInfo(ServerWorld serverWorld, BlockPos blockPos, BlockState blockState) {
        List<Text> lines = new ArrayList<>();
        Block block = blockState.getBlock();
        Text name = block.getName();
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.name", name));

        Optional<RegistryEntry<PointOfInterestType>> optionalRegistryEntry =
                PointOfInterestTypes.getTypeForState(blockState);
        if (optionalRegistryEntry.isEmpty()) {
            lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.poiType",
                    Text.translatable("text.LibertyVillagers.villagerInfo.none")));
            return lines;
        }

        PointOfInterestType poiType = optionalRegistryEntry.get().value();
        Optional<RegistryKey<PointOfInterestType>> optionalRegistryKey = optionalRegistryEntry.get().getKey();
        if (optionalRegistryKey.isEmpty()) {
            lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.poiType",
                    Text.translatable("text.LibertyVillagers.villagerInfo.none")));
            return lines;
        }

        String poiTypeName = optionalRegistryKey.get().getValue().toString();

        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.poiType", poiTypeName));

        PointOfInterestStorage storage = serverWorld.getPointOfInterestStorage();
        if (!storage.hasTypeAt(optionalRegistryKey.get(), blockPos)) {
            lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.notAPOI"));
            return lines;
        }

        int freeTickets = storage.getFreeTickets(blockPos);
        Text isOccupied =
                freeTickets < poiType.ticketCount() ? Text.translatable("text.LibertyVillagers.villagerInfo.true") :
                        Text.translatable("text" + ".LibertyVillagers.villagerInfo.false");
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.isOccupied", isOccupied));
        lines.add(Text.translatable("text.LibertyVillagers.villagerInfo.freeTickets", freeTickets,
                poiType.ticketCount()));

        return lines;

    }
}