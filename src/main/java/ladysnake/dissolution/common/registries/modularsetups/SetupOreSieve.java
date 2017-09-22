package ladysnake.dissolution.common.registries.modularsetups;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableSet;

import ladysnake.dissolution.api.EssentiaStack;
import ladysnake.dissolution.api.EssentiaTypes;
import ladysnake.dissolution.api.IEssentiaHandler;
import ladysnake.dissolution.common.Reference;
import ladysnake.dissolution.common.blocks.alchemysystem.BlockCasing;
import ladysnake.dissolution.common.blocks.alchemysystem.BlockCasing.EnumPartType;
import ladysnake.dissolution.common.capabilities.CapabilityEssentiaHandler;
import ladysnake.dissolution.common.init.ModBlocks;
import ladysnake.dissolution.common.init.ModItems;
import ladysnake.dissolution.common.items.AlchemyModule;
import ladysnake.dissolution.common.items.ItemAlchemyModule;
import ladysnake.dissolution.common.tileentities.TileEntityModularMachine;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

public class SetupOreSieve extends ModularMachineSetup {

	private final Map<Item, Item> conversions;
	private final Map<Item, EssentiaStack> essentiaConversions;
	private static final ImmutableSet<ItemAlchemyModule> setup = ImmutableSet.of(
			ItemAlchemyModule.getFromType(AlchemyModule.CONTAINER, 1),
			ItemAlchemyModule.getFromType(AlchemyModule.MATERIAL_INTERFACE, 1),
			ItemAlchemyModule.getFromType(AlchemyModule.MINERAL_FILTER, 1));

	public SetupOreSieve() {
		this.setRegistryName(new ResourceLocation(Reference.MOD_ID, "ore_sieve"));
		this.conversions = new HashMap<>();
		this.essentiaConversions = new HashMap<>();
		addConversion(Blocks.CLAY, ModBlocks.DEPLETED_CLAY, new EssentiaStack(EssentiaTypes.SALIS, 1));
		addConversion(Blocks.COAL_BLOCK, ModBlocks.DEPLETED_COAL, new EssentiaStack(EssentiaTypes.CINNABARIS, 1));
		addConversion(Blocks.MAGMA, ModBlocks.DEPLETED_MAGMA, new EssentiaStack(EssentiaTypes.SULPURIS, 1));
	}

	private void addConversion(Block input, Block output, EssentiaStack essentiaOutput) {
		this.conversions.put(Item.getItemFromBlock(input), Item.getItemFromBlock(output));
		this.essentiaConversions.put(Item.getItemFromBlock(input), essentiaOutput);
	}

	@Override
	public ISetupInstance getInstance(TileEntityModularMachine te) {
		return new Instance(te);
	}

	@Override
	public ImmutableSet<ItemAlchemyModule> getSetup() {
		return setup;
	}

	public class Instance implements ISetupInstance {

		TileEntityModularMachine tile;
		private InputItemHandler input;
		private OutputItemHandler depletedOutput;
		private IEssentiaHandler essentiaOutput;
		private int progressTicks;
		private int processingTime;

		public Instance(TileEntityModularMachine te) {
			super();
			this.tile = te;
			this.input = new InputItemHandler(Blocks.CLAY, Blocks.COAL_BLOCK, Blocks.MAGMA);
			this.essentiaOutput = new CapabilityEssentiaHandler.DefaultEssentiaHandler(100);
			this.depletedOutput = new OutputItemHandler();
			this.processingTime = AlchemyModule.MINERAL_FILTER.maxTier / this.tile.getInstalledModules().stream()
					.filter(mod -> mod.getType() == AlchemyModule.MINERAL_FILTER).findAny().get().getTier();
			this.input.insertItem(0, new ItemStack(Blocks.COAL_BLOCK, 64), false);
		}

		@Override
		public void onTick() {
			if (tile.isPowered() && !tile.getWorld().isRemote && !input.getStackInSlot(0).isEmpty()) {
				if (progressTicks++ % (processingTime * 20) == 0
						&& depletedOutput.getStackInSlot(0).getCount() < depletedOutput.getSlotLimit(0)
						&& !essentiaOutput.isFull()) {
					ItemStack inputStack = input.extractItem(0, 1, false);
					depletedOutput.insertItemInternal(0, new ItemStack(conversions.get(inputStack.getItem())), false);
					depletedOutput.insertItemInternal(0,
							tile.tryOutput(depletedOutput.extractItem(0, 10, false), EnumFacing.WEST), false);

					essentiaOutput.insert(essentiaConversions.get(inputStack.getItem()));
					EnumFacing essentiaOutputFace = tile.adjustFaceOut(EnumFacing.NORTH);
					TileEntity te = tile.getWorld().getTileEntity(tile.getPos().offset(essentiaOutputFace));
					if (te != null && te.hasCapability(CapabilityEssentiaHandler.CAPABILITY_ESSENTIA,
							essentiaOutputFace.getOpposite()))
						this.essentiaOutput.flow(te.getCapability(CapabilityEssentiaHandler.CAPABILITY_ESSENTIA,
								essentiaOutputFace.getOpposite()));
				}
			}
		}

		@Override
		public boolean hasCapability(Capability<?> capability, EnumFacing facing, EnumPartType part) {
			if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
				return (part == BlockCasing.EnumPartType.TOP && facing == EnumFacing.EAST)
						|| (part == BlockCasing.EnumPartType.BOTTOM && facing == EnumFacing.WEST);
			else if (capability == CapabilityEssentiaHandler.CAPABILITY_ESSENTIA)
				return (part == BlockCasing.EnumPartType.BOTTOM && facing == EnumFacing.NORTH);
			return false;
		}

		@Override
		public <T> T getCapability(Capability<T> capability, EnumFacing facing, EnumPartType part) {
			if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
				if (part == BlockCasing.EnumPartType.TOP && facing == EnumFacing.EAST)
					return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(input);
				else if (part == BlockCasing.EnumPartType.BOTTOM && facing == EnumFacing.WEST)
					return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(depletedOutput);
			} else if (capability == CapabilityEssentiaHandler.CAPABILITY_ESSENTIA) {
				if (part == BlockCasing.EnumPartType.BOTTOM && facing == EnumFacing.NORTH)
					return CapabilityEssentiaHandler.CAPABILITY_ESSENTIA.cast(essentiaOutput);
			}
			return null;
		}
		
		@Override
		public void readFromNBT(NBTTagCompound compound) {
			CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.getStorage().readNBT(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, input, EnumFacing.EAST, compound.getTag("input"));
			CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.getStorage().readNBT(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, depletedOutput, EnumFacing.EAST, compound.getTag("output"));
			CapabilityEssentiaHandler.CAPABILITY_ESSENTIA.getStorage().readNBT(CapabilityEssentiaHandler.CAPABILITY_ESSENTIA, essentiaOutput, EnumFacing.NORTH, compound.getTag("essentiaOutput"));
		}
		
		@Override
		public NBTTagCompound writeToNBT(NBTTagCompound compound) {
			compound.setTag("input", CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.getStorage().writeNBT(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, this.input, EnumFacing.EAST));
			compound.setTag("output", CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.getStorage().writeNBT(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, this.depletedOutput, EnumFacing.WEST));
			compound.setTag("essentiaOutput", CapabilityEssentiaHandler.CAPABILITY_ESSENTIA.getStorage().writeNBT(CapabilityEssentiaHandler.CAPABILITY_ESSENTIA, essentiaOutput, EnumFacing.NORTH));
			return compound;
		}
	}

}