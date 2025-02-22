package gregtech.api.util;

import codechicken.nei.PositionedStack;
import gregtech.api.GregTech_API;
import gregtech.api.enums.*;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IHasWorldObjectAndCoords;
import gregtech.api.objects.GT_FluidStack;
import gregtech.api.objects.GT_ItemStack;
import gregtech.api.objects.ItemData;
import gregtech.api.objects.MaterialStack;
import gregtech.nei.GT_NEI_DefaultHandler.FixedPositionedStack;
import ic2.core.Ic2Items;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.cliffc.high_scale_lib.NonBlockingHashSet;

import java.util.*;

import static gregtech.api.enums.GT_Values.*;

/**
 * NEVER INCLUDE THIS FILE IN YOUR MOD!!!
 * <p/>
 * This File contains the functions used for Recipes. Please do not include this File AT ALL in your Moddownload as it ruins compatibility
 * This is just the Core of my Recipe System, if you just want to GET the Recipes I add, then you can access this File.
 * Do NOT add Recipes using the Constructors inside this Class, The GregTech_API File calls the correct Functions for these Constructors.
 * <p/>
 * I know this File causes some Errors, because of missing Main Functions, but if you just need to compile Stuff, then remove said erroreous Functions.
 */
public class GT_Recipe implements Comparable<GT_Recipe> {
    public static volatile int VERSION = 509;
    /**
     * If you want to change the Output, feel free to modify or even replace the whole ItemStack Array, for Inputs, please add a new Recipe, because of the HashMaps.
     */
    public ItemStack[] mInputs, mOutputs;
    /**
     * If you want to change the Output, feel free to modify or even replace the whole ItemStack Array, for Inputs, please add a new Recipe, because of the HashMaps.
     */
    public FluidStack[] mFluidInputs, mFluidOutputs;
    /**
     * If you changed the amount of Array-Items inside the Output Array then the length of this Array must be larger or equal to the Output Array. A chance of 10000 equals 100%
     */
    public int[] mChances;
    /**
     * An Item that needs to be inside the Special Slot, like for example the Copy Slot inside the Printer. This is only useful for Fake Recipes in NEI, since findRecipe() and containsInput() don't give a shit about this Field. Lists are also possible.
     */
    public Object mSpecialItems;
    public int mDuration, mEUt, mSpecialValue;
    /**
     * Use this to just disable a specific Recipe, but the Configuration enables that already for every single Recipe.
     */
    public boolean mEnabled = true;
    /**
     * If this Recipe is hidden from NEI
     */
    public boolean mHidden = false;
    /**
     * If this Recipe is Fake and therefore doesn't get found by the findRecipe Function (It is still in the HashMaps, so that containsInput does return T on those fake Inputs)
     */
    public boolean mFakeRecipe = false;
    /**
     * If this Recipe can be stored inside a Machine in order to make Recipe searching more Efficient by trying the previously used Recipe first. In case you have a Recipe Map overriding things and returning one time use Recipes, you have to set this to F.
     */
    public boolean mCanBeBuffered = true;
    /**
     * If this Recipe needs the Output Slots to be completely empty. Needed in case you have randomised Outputs
     */
    public boolean mNeedsEmptyOutput = false;
    /**
     * Used for describing recipes that do not fit the default recipe pattern (for example Large Boiler Fuels)
     */
    private String[] neiDesc = null;
    
    private GT_Recipe(GT_Recipe aRecipe) {
        mInputs = GT_Utility.copyStackArray((Object[]) aRecipe.mInputs);
        mOutputs = GT_Utility.copyStackArray((Object[]) aRecipe.mOutputs);
        mSpecialItems = aRecipe.mSpecialItems;
        mChances = aRecipe.mChances;
        mFluidInputs = GT_Utility.copyFluidArray(aRecipe.mFluidInputs);
        mFluidOutputs = GT_Utility.copyFluidArray(aRecipe.mFluidOutputs);
        mDuration = aRecipe.mDuration;
        mSpecialValue = aRecipe.mSpecialValue;
        mEUt = aRecipe.mEUt;
        mNeedsEmptyOutput = aRecipe.mNeedsEmptyOutput;
        mCanBeBuffered = aRecipe.mCanBeBuffered;
        mFakeRecipe = aRecipe.mFakeRecipe;
        mEnabled = aRecipe.mEnabled;
        mHidden = aRecipe.mHidden;
    }
    protected GT_Recipe(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecialItems, int[] aChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
        if (aInputs == null) aInputs = new ItemStack[0];
        if (aOutputs == null) aOutputs = new ItemStack[0];
        if (aFluidInputs == null) aFluidInputs = new FluidStack[0];
        if (aFluidOutputs == null) aFluidOutputs = new FluidStack[0];
        if (aChances == null) aChances = new int[aOutputs.length];
        if (aChances.length < aOutputs.length) aChances = Arrays.copyOf(aChances, aOutputs.length);

        aInputs = GT_Utility.getArrayListWithoutTrailingNulls(aInputs).toArray(new ItemStack[0]);
        aOutputs = GT_Utility.getArrayListWithoutTrailingNulls(aOutputs).toArray(new ItemStack[0]);
        aFluidInputs = GT_Utility.getArrayListWithoutNulls(aFluidInputs).toArray(new FluidStack[0]);
        aFluidOutputs = GT_Utility.getArrayListWithoutNulls(aFluidOutputs).toArray(new FluidStack[0]);

        GT_OreDictUnificator.setStackArray(true, aInputs);
        GT_OreDictUnificator.setStackArray(true, aOutputs);

        for (ItemStack tStack : aOutputs) GT_Utility.updateItemStack(tStack);

        for (int i = 0; i < aChances.length; i++) if (aChances[i] <= 0) aChances[i] = 10000;
        for (int i = 0; i < aFluidInputs.length; i++) aFluidInputs[i] = new GT_FluidStack(aFluidInputs[i]);
        for (int i = 0; i < aFluidOutputs.length; i++) aFluidOutputs[i] = new GT_FluidStack(aFluidOutputs[i]);

        for (int i = 0; i < aInputs.length; i++)
            if (aInputs[i] != null && Items.feather.getDamage(aInputs[i]) != W)
                for (int j = 0; j < aOutputs.length; j++) {
                    if (GT_Utility.areStacksEqual(aInputs[i], aOutputs[j])) {
                        if (aInputs[i].stackSize >= aOutputs[j].stackSize) {
                            aInputs[i].stackSize -= aOutputs[j].stackSize;
                            aOutputs[j] = null;
                        } else {
                            aOutputs[j].stackSize -= aInputs[i].stackSize;
                        }
                    }
                }

        if (aOptimize && aDuration >= 32) {
            ArrayList<ItemStack> tList = new ArrayList<ItemStack>();
            tList.addAll(Arrays.asList(aInputs));
            tList.addAll(Arrays.asList(aOutputs));
            for (int i = 0; i < tList.size(); i++) if (tList.get(i) == null) tList.remove(i--);

            for (byte i = (byte) Math.min(64, aDuration / 16); i > 1; i--)
                if (aDuration / i >= 16) {
                    boolean temp = true;
                    for (int j = 0, k = tList.size(); temp && j < k; j++)
                        if (tList.get(j).stackSize % i != 0) temp = false;
                    for (int j = 0; temp && j < aFluidInputs.length; j++)
                        if (aFluidInputs[j].amount % i != 0) temp = false;
                    for (int j = 0; temp && j < aFluidOutputs.length; j++)
                        if (aFluidOutputs[j].amount % i != 0) temp = false;
                    if (temp) {
                        for (int j = 0, k = tList.size(); j < k; j++) tList.get(j).stackSize /= i;
                        for (int j = 0; j < aFluidInputs.length; j++) aFluidInputs[j].amount /= i;
                        for (int j = 0; j < aFluidOutputs.length; j++) aFluidOutputs[j].amount /= i;
                        aDuration /= i;
                    }
                }
        }

        mInputs = aInputs;
        mOutputs = aOutputs;
        mSpecialItems = aSpecialItems;
        mChances = aChances;
        mFluidInputs = aFluidInputs;
        mFluidOutputs = aFluidOutputs;
        mDuration = aDuration;
        mSpecialValue = aSpecialValue;
        mEUt = aEUt;
//		checkCellBalance();
    }

    public GT_Recipe(ItemStack aInput1, ItemStack aOutput1, int aFuelValue, int aType) {
        this(aInput1, aOutput1, null, null, null, aFuelValue, aType);
    }

    // aSpecialValue = EU per Liter! If there is no Liquid for this Object, then it gets multiplied with 1000!
    public GT_Recipe(ItemStack aInput1, ItemStack aOutput1, ItemStack aOutput2, ItemStack aOutput3, ItemStack aOutput4, int aSpecialValue, int aType) {
        this(true, new ItemStack[]{aInput1}, new ItemStack[]{aOutput1, aOutput2, aOutput3, aOutput4}, null, null, null, null, 0, 0, Math.max(1, aSpecialValue));

        if (mInputs.length > 0 && aSpecialValue > 0) {
            switch (aType) {
                // Diesel Generator
                case 0:
                    GT_Recipe_Map.sDieselFuels.addRecipe(this);
                    GT_Recipe_Map.sLargeBoilerFakeFuels.addDieselRecipe(this);
                    break;
                // Gas Turbine
                case 1:
                    GT_Recipe_Map.sTurbineFuels.addRecipe(this);
                    break;
                // Thermal Generator
                case 2:
                    GT_Recipe_Map.sHotFuels.addRecipe(this);
                    break;
                // Plasma Generator
                case 4:
                    GT_Recipe_Map.sPlasmaFuels.addRecipe(this);
                    break;
                // Magic Generator
                case 5:
                    GT_Recipe_Map.sMagicFuels.addRecipe(this);
                    break;
                // Fluid Generator. Usually 3. Every wrong Type ends up in the Semifluid Generator
                default:
                    GT_Recipe_Map.sDenseLiquidFuels.addRecipe(this);
                    GT_Recipe_Map.sLargeBoilerFakeFuels.addDenseLiquidRecipe(this);
                    break;
            }
        }
    }

    public GT_Recipe(FluidStack aInput1, FluidStack aInput2, FluidStack aOutput1, int aDuration, int aEUt, int aSpecialValue) {
        this(true, null, null, null, null, new FluidStack[]{aInput1, aInput2}, new FluidStack[]{aOutput1}, Math.max(aDuration, 1), aEUt, Math.max(Math.min(aSpecialValue, 160000000), 0));
        if (mInputs.length > 1) {
            GT_Recipe_Map.sFusionRecipes.addRecipe(this);
        }
    }

    public GT_Recipe(ItemStack aInput1, ItemStack aOutput1, ItemStack aOutput2, int aDuration, int aEUt) {
        this(true, new ItemStack[]{aInput1}, new ItemStack[]{aOutput1, aOutput2}, null, null, null, null, aDuration, aEUt, 0);
        if (mInputs.length > 0 && mOutputs[0] != null) {
            GT_Recipe_Map.sLatheRecipes.addRecipe(this);
        }
    }

    public GT_Recipe(ItemStack aInput1, int aCellAmount, ItemStack aOutput1, ItemStack aOutput2, ItemStack aOutput3, ItemStack aOutput4, int aDuration, int aEUt) {
        this(true, new ItemStack[]{aInput1, aCellAmount > 0 ? ItemList.Cell_Empty.get(Math.min(64, Math.max(1, aCellAmount))) : null}, new ItemStack[]{aOutput1, aOutput2, aOutput3, aOutput4}, null, null, null, null, Math.max(aDuration, 1), Math.max(aEUt, 1), 0);
        if (mInputs.length > 0 && mOutputs[0] != null) {
            GT_Recipe_Map.sDistillationRecipes.addRecipe(this);
        }
    }

    public GT_Recipe(ItemStack aInput1, int aInput2, ItemStack aOutput1, ItemStack aOutput2) {
        this(true, new ItemStack[]{aInput1, GT_ModHandler.getIC2Item("industrialTnt", aInput2 > 0 ? aInput2 < 64 ? aInput2 : 64 : 1, new ItemStack(Blocks.tnt, aInput2 > 0 ? aInput2 < 64 ? aInput2 : 64 : 1))}, new ItemStack[]{aOutput1, aOutput2}, null, null, null, null, 20, 30, 0);
        if (mInputs.length > 0 && mOutputs[0] != null) {
            GT_Recipe_Map.sImplosionRecipes.addRecipe(this);
        }
    }

    public GT_Recipe(int aEUt, int aDuration, ItemStack aInput1, ItemStack aOutput1) {
        this(true, new ItemStack[]{aInput1, ItemList.Circuit_Integrated.getWithDamage(0, aInput1.stackSize)}, new ItemStack[]{aOutput1}, null, null, null, null, Math.max(aDuration, 1), Math.max(aEUt, 1), 0);
        if (mInputs.length > 0 && mOutputs[0] != null) {
            GT_Recipe_Map.sBenderRecipes.addRecipe(this);
        }
    }

    public GT_Recipe(ItemStack aInput1, ItemStack aInput2, int aEUt, int aDuration, ItemStack aOutput1) {
        this(true, aInput2 == null ? new ItemStack[]{aInput1} : new ItemStack[]{aInput1, aInput2}, new ItemStack[]{aOutput1}, null, null, null, null, Math.max(aDuration, 1), Math.max(aEUt, 1), 0);
        if (mInputs.length > 0 && mOutputs[0] != null) {
            GT_Recipe_Map.sAlloySmelterRecipes.addRecipe(this);
        }
    }

    public GT_Recipe(ItemStack aInput1, int aEUt, ItemStack aInput2, int aDuration, ItemStack aOutput1, ItemStack aOutput2) {
        this(true, aInput2 == null ? new ItemStack[]{aInput1} : new ItemStack[]{aInput1, aInput2}, new ItemStack[]{aOutput1, aOutput2}, null, null, null, null, Math.max(aDuration, 1), Math.max(aEUt, 1), 0);
        if (mInputs.length > 0 && mOutputs[0] != null) {
            GT_Recipe_Map.sCannerRecipes.addRecipe(this);
        }
    }

    public GT_Recipe(ItemStack aInput1, ItemStack aOutput1, int aDuration) {
        this(true, new ItemStack[]{aInput1}, new ItemStack[]{aOutput1}, null, null, null, null, Math.max(aDuration, 1), 120, 0);
        if (mInputs.length > 0 && mOutputs[0] != null) {
            GT_Recipe_Map.sVacuumRecipes.addRecipe(this);
        }
    }

    public GT_Recipe(ItemStack aInput1, ItemStack aOutput1, int aDuration, int aEUt, int VACUUM) {
        this(true, new ItemStack[]{aInput1}, new ItemStack[]{aOutput1}, null, null, null, null, Math.max(aDuration, 1), aEUt, 0);
        if (mInputs.length > 0 && mOutputs[0] != null) {
            GT_Recipe_Map.sVacuumRecipes.addRecipe(this);
        }
    }

    //Dummy GT_Recipe maker...
    public GT_Recipe(ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecialItems, int[] aChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue){
        this(true, aInputs, aOutputs, aSpecialItems, aChances, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue);
    }

    public static void reInit() {
        GT_Log.out.println("GT_Mod: Re-Unificating Recipes.");
        for (GT_Recipe_Map tMapEntry : GT_Recipe_Map.sMappings)
            tMapEntry.reInit();
    }

    // -----
    // Old Constructors, do not use!
    // -----

    public ItemStack getRepresentativeInput(int aIndex) {
        if (aIndex < 0 || aIndex >= mInputs.length) return null;
        return GT_Utility.copy(mInputs[aIndex]);
    }

    public ItemStack getOutput(int aIndex) {
        if (aIndex < 0 || aIndex >= mOutputs.length) return null;
        return GT_Utility.copy(mOutputs[aIndex]);
    }

    public int getOutputChance(int aIndex) {
        if (aIndex < 0 || aIndex >= mChances.length) return 10000;
        return mChances[aIndex];
    }

    public FluidStack getRepresentativeFluidInput(int aIndex) {
        if (aIndex < 0 || aIndex >= mFluidInputs.length || mFluidInputs[aIndex] == null) return null;
        return mFluidInputs[aIndex].copy();
    }

    public FluidStack getFluidOutput(int aIndex) {
        if (aIndex < 0 || aIndex >= mFluidOutputs.length || mFluidOutputs[aIndex] == null) return null;
        return mFluidOutputs[aIndex].copy();
    }

    public void checkCellBalance() {
        if (!D2 || mInputs.length < 1) return;

        int tInputAmount = GT_ModHandler.getCapsuleCellContainerCountMultipliedWithStackSize(mInputs);
        int tOutputAmount = GT_ModHandler.getCapsuleCellContainerCountMultipliedWithStackSize(mOutputs);

        if (tInputAmount < tOutputAmount) {
            if (!Materials.Tin.contains(mInputs)) {
                GT_Log.err.println("You get more Cells, than you put in? There must be something wrong.");
                new Exception().printStackTrace(GT_Log.err);
            }
        } else if (tInputAmount > tOutputAmount) {
            if (!Materials.Tin.contains(mOutputs)) {
                GT_Log.err.println("You get less Cells, than you put in? GT Machines usually don't destroy Cells.");
                new Exception().printStackTrace(GT_Log.err);
            }
        }
    }

    public GT_Recipe copy() {
        return new GT_Recipe(this);
    }

    public boolean isRecipeInputEqual(boolean aDecreaseStacksizeBySuccess, FluidStack[] aFluidInputs, ItemStack... aInputs) {
        return isRecipeInputEqual(aDecreaseStacksizeBySuccess, false, aFluidInputs, aInputs);
    }

    public static boolean GTppRecipeHelper;

    public boolean isRecipeInputEqual(boolean aDecreaseStacksizeBySuccess, boolean aDontCheckStackSizes, FluidStack[] aFluidInputs, ItemStack... aInputs) {
        if (mFluidInputs.length > 0 && aFluidInputs == null) return false;
        int amt;
        for (FluidStack tFluid : mFluidInputs)
            if (tFluid != null) {
                boolean temp = true;
                amt = tFluid.amount;
                for (FluidStack aFluid : aFluidInputs)
                    if (aFluid != null && aFluid.isFluidEqual(tFluid)) {
                        if (aDontCheckStackSizes) {
                            temp = false;
                            break;
                        }
                        amt -= aFluid.amount;
                        if (amt < 1) {
                            temp = false;
                            break;
                        }
                    }
                if (temp) return false;
            }

        if (mInputs.length > 0 && aInputs == null) return false;

        for (ItemStack tStack : mInputs) {
            if (tStack != null) {
                amt = tStack.stackSize;
                boolean temp = true;
                for (ItemStack aStack : aInputs) {
                    if ((GT_Utility.areUnificationsEqual(aStack, tStack, true) || GT_Utility.areUnificationsEqual(GT_OreDictUnificator.get(false, aStack), tStack, true))) {
                        if (GTppRecipeHelper) {//remove once the fix is out
                            if (GT_Utility.areStacksEqual(aStack, Ic2Items.FluidCell.copy(), true) || GT_Utility.areStacksEqual(aStack, ItemList.Tool_DataStick.get(1L), true) || GT_Utility.areStacksEqual(aStack, ItemList.Tool_DataOrb.get(1L), true)) {
                                if (!GT_Utility.areStacksEqual(aStack, tStack, false))
                                    continue;
                            }
                        }
                        if (aDontCheckStackSizes) {
                            temp = false;
                            break;
                        }
                        amt -= aStack.stackSize;
                        if (amt < 1) {
                            temp = false;
                            break;
                        }
                    }
                }
                if (temp) return false;
            }
        }
        if (aDecreaseStacksizeBySuccess) {
            if (aFluidInputs != null) {
                for (FluidStack tFluid : mFluidInputs) {
                    if (tFluid != null) {
                        amt = tFluid.amount;
                        for (FluidStack aFluid : aFluidInputs) {
                            if (aFluid != null && aFluid.isFluidEqual(tFluid)) {
                                if (aDontCheckStackSizes) {
                                    aFluid.amount -= amt;
                                    break;
                                }
                                if (aFluid.amount < amt) {
                                    amt -= aFluid.amount;
                                    aFluid.amount = 0;
                                } else {
                                    aFluid.amount -= amt;
                                    amt = 0;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (aInputs != null) {
                for (ItemStack tStack : mInputs) {
                    if (tStack != null) {
                        amt = tStack.stackSize;
                        for (ItemStack aStack : aInputs) {
                            if ((GT_Utility.areUnificationsEqual(aStack, tStack, true) || GT_Utility.areUnificationsEqual(GT_OreDictUnificator.get(false, aStack), tStack, true))) {
                                if (GTppRecipeHelper) {
                                    if (GT_Utility.areStacksEqual(aStack, Ic2Items.FluidCell.copy(), true) || GT_Utility.areStacksEqual(aStack, ItemList.Tool_DataStick.get(1L), true) || GT_Utility.areStacksEqual(aStack, ItemList.Tool_DataOrb.get(1L), true)) {
                                        if (!GT_Utility.areStacksEqual(aStack, tStack, false))
                                            continue;
                                    }
                                }
                                if (aDontCheckStackSizes){
                                    aStack.stackSize -= amt;
                                    break;
                                }
                                if (aStack.stackSize < amt){
                                    amt -= aStack.stackSize;
                                    aStack.stackSize = 0;
                                }else{
                                    aStack.stackSize -= amt;
                                    amt = 0;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    @Override
    public int compareTo(GT_Recipe recipe) {
        // first lowest tier recipes
        // then fastest
        // then with lowest special value
        // then dry recipes
        // then with fewer inputs
        if (this.mEUt != recipe.mEUt) {
            return this.mEUt - recipe.mEUt;
        } else if (this.mDuration != recipe.mDuration) {
            return this.mDuration - recipe.mDuration;
        } else if (this.mSpecialValue != recipe.mSpecialValue) {
            return this.mSpecialValue - recipe.mSpecialValue;
        } else if (this.mFluidInputs.length != recipe.mFluidInputs.length) {
            return this.mFluidInputs.length - recipe.mFluidInputs.length;
        } else if (this.mInputs.length != recipe.mInputs.length) {
            return this.mInputs.length - recipe.mInputs.length;
        }
        return 0;
    }

    public String[] getNeiDesc() {
        return neiDesc;
    }

    protected void setNeiDesc(String... neiDesc) {
        this.neiDesc = neiDesc;
    }

    /**
     * Overriding this method and getOutputPositionedStacks allows for custom NEI stack placement
     * @return A list of input stacks
     */
    public ArrayList<PositionedStack> getInputPositionedStacks(){
    	return null;
    }

    /**
     * Overriding this method and getInputPositionedStacks allows for custom NEI stack placement
     * @return A list of output stacks
     */
    public ArrayList<PositionedStack> getOutputPositionedStacks(){
    	return null;
    }

	public static class GT_Recipe_AssemblyLine{
        public static final ArrayList<GT_Recipe_AssemblyLine> sAssemblylineRecipes = new ArrayList<GT_Recipe_AssemblyLine>();
        
        public ItemStack mResearchItem;
        public int mResearchTime;
        public ItemStack[] mInputs;
        public FluidStack[] mFluidInputs;
        public ItemStack mOutput;
        public int mDuration;
        public int mEUt;
        public ItemStack[][] mOreDictAlt;

        public GT_Recipe_AssemblyLine(ItemStack aResearchItem, int aResearchTime, ItemStack[] aInputs, FluidStack[] aFluidInputs, ItemStack aOutput, int aDuration, int aEUt) {
        	this(aResearchItem, aResearchTime, aInputs, aFluidInputs, aOutput, aDuration, aEUt, new ItemStack[aInputs.length][]);
        }
        
        public GT_Recipe_AssemblyLine(ItemStack aResearchItem, int aResearchTime, ItemStack[] aInputs, FluidStack[] aFluidInputs, ItemStack aOutput, int aDuration, int aEUt, ItemStack[][] aAlt) {
        	mResearchItem = aResearchItem;
        	mResearchTime = aResearchTime;
        	mInputs = aInputs;
        	mFluidInputs = aFluidInputs;
        	mOutput = aOutput;
        	mDuration = aDuration;
        	mEUt = aEUt;
        	mOreDictAlt = aAlt;
        }
        
    }

    public static class GT_Recipe_Map {
        /**
         * Contains all Recipe Maps
         */
        public static final Collection<GT_Recipe_Map> sMappings = new ArrayList<GT_Recipe_Map>();

        public static final GT_Recipe_Map sOreWasherRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.orewasher", "Ore Washing Plant", null, RES_PATH_GUI + "basicmachines/OreWasher", 1, 3, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sThermalCentrifugeRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.thermalcentrifuge", "Thermal Centrifuge", null, RES_PATH_GUI + "basicmachines/ThermalCentrifuge", 1, 3, 1, 0, 2, E, 1, E, true, true);
        public static final GT_Recipe_Map sCompressorRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.compressor", "Compressor", null, RES_PATH_GUI + "basicmachines/Compressor", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sExtractorRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.extractor", "Extractor", null, RES_PATH_GUI + "basicmachines/Extractor", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sRecyclerRecipes = new GT_Recipe_Map_Recycler(new NonBlockingHashSet<>(), "ic.recipe.recycler", "Recycler", "ic2.recycler", RES_PATH_GUI + "basicmachines/Recycler", 1, 1, 1, 0, 1, E, 1, E, true, false);
        public static final GT_Recipe_Map sFurnaceRecipes = new GT_Recipe_Map_Furnace(new NonBlockingHashSet<>(), "mc.recipe.furnace", "Furnace", "smelting", RES_PATH_GUI + "basicmachines/E_Furnace", 1, 1, 1, 0, 1, E, 1, E, true, false);
        public static final GT_Recipe_Map sMicrowaveRecipes = new GT_Recipe_Map_Microwave(new NonBlockingHashSet<>(), "gt.recipe.microwave", "Microwave", "smelting", RES_PATH_GUI + "basicmachines/E_Furnace", 1, 1, 1, 0, 1, E, 1, E, true, false);

        public static final GT_Recipe_Map sScannerFakeRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.scanner", "Scanner", null, RES_PATH_GUI + "basicmachines/Scanner", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sRockBreakerFakeRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.rockbreaker", "Rock Breaker", null, RES_PATH_GUI + "basicmachines/RockBreaker", 1, 1, 0, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sByProductList = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.byproductlist", "Ore Byproduct List", null, RES_PATH_GUI + "basicmachines/Default", 1, 6, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sReplicatorFakeRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.replicator", "Replicator", null, RES_PATH_GUI + "basicmachines/Replicator", 0, 1, 0, 1, 1, E, 1, E, true, true);
        //public static final GT_Recipe_Map sAssemblylineFakeRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.scanner", "Scanner", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sAssemblylineVisualRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.fakeAssemblylineProcess", "Assemblyline Process", null, RES_PATH_GUI + "FakeAssemblyline", 1, 1, 1, 0, 1, E, 1, E, true, false);
        public static final GT_Recipe_Map sPlasmaArcFurnaceRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.plasmaarcfurnace", "Plasma Arc Furnace", null, RES_PATH_GUI + "basicmachines/PlasmaArcFurnace", 1, 4, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sArcFurnaceRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.arcfurnace", "Arc Furnace", null, RES_PATH_GUI + "basicmachines/ArcFurnace", 1, 4, 1, 1, 3, E, 1, E, true, true);
        public static final GT_Recipe_Map sPrinterRecipes = new GT_Recipe_Map_Printer(new NonBlockingHashSet<>(), "gt.recipe.printer", "Printer", null, RES_PATH_GUI + "basicmachines/Printer", 1, 1, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sSifterRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.sifter", "Sifter", null, RES_PATH_GUI + "basicmachines/Sifter", 1, 9, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sPressRecipes = new GT_Recipe_Map_FormingPress(new NonBlockingHashSet<>(), "gt.recipe.press", "Forming Press", null, RES_PATH_GUI + "basicmachines/Press", 2, 1, 2, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sLaserEngraverRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.laserengraver", "Precision Laser Engraver", null, RES_PATH_GUI + "basicmachines/LaserEngraver", 2, 1, 2, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sMixerRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.mixer", "Mixer", null, RES_PATH_GUI + "basicmachines/Mixer", 6, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sAutoclaveRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.autoclave", "Autoclave", null, RES_PATH_GUI + "basicmachines/Autoclave", 1, 1, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sElectroMagneticSeparatorRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.electromagneticseparator", "Electromagnetic Separator", null, RES_PATH_GUI + "basicmachines/ElectromagneticSeparator", 1, 3, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sPolarizerRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.polarizer", "Electromagnetic Polarizer", null, RES_PATH_GUI + "basicmachines/Polarizer", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sMaceratorRecipes = new GT_Recipe_Map_Macerator(new NonBlockingHashSet<>(), "gt.recipe.macerator", "Pulverization", null, RES_PATH_GUI + "basicmachines/Macerator4", 1, 4, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sChemicalBathRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.chemicalbath", "Chemical Bath", null, RES_PATH_GUI + "basicmachines/ChemicalBath", 1, 3, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sFluidCannerRecipes = new GT_Recipe_Map_FluidCanner(new NonBlockingHashSet<>(), "gt.recipe.fluidcanner", "Fluid Canning Machine", null, RES_PATH_GUI + "basicmachines/FluidCannerNEI", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sBrewingRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.brewer", "Brewing Machine", null, RES_PATH_GUI + "basicmachines/PotionBrewer", 1, 0, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sFluidHeaterRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.fluidheater", "Fluid Heater", null, RES_PATH_GUI + "basicmachines/FluidHeater", 1, 0, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sDistilleryRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.distillery", "Distillery", null, RES_PATH_GUI + "basicmachines/Distillery", 1, 1, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sFermentingRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.fermenter", "Fermenter", null, RES_PATH_GUI + "basicmachines/Fermenter", 0, 0, 0, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sFluidSolidficationRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.fluidsolidifier", "Fluid Solidifier", null, RES_PATH_GUI + "basicmachines/FluidSolidifier", 1, 1, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sFluidExtractionRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.fluidextractor", "Fluid Extractor", null, RES_PATH_GUI + "basicmachines/FluidExtractor", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sBoxinatorRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.packager", "Packager", null, RES_PATH_GUI + "basicmachines/Packager", 2, 1, 2, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sUnboxinatorRecipes = new GT_Recipe_Map_Unboxinator(new NonBlockingHashSet<>(), "gt.recipe.unpackager", "Unpackager", null, RES_PATH_GUI + "basicmachines/Unpackager", 1, 2, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sFusionRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.fusionreactor", "Fusion Reactor", null, RES_PATH_GUI + "basicmachines/FusionReactor", 0, 0, 0, 2, 1, "Start: ", 1, " EU", true, true);
        public static final GT_Recipe_Map sCentrifugeRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.centrifuge", "Centrifuge", null, RES_PATH_GUI + "basicmachines/Centrifuge", 2, 6, 0, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sElectrolyzerRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.electrolyzer", "Electrolyzer", null, RES_PATH_GUI + "basicmachines/Electrolyzer", 2, 6, 0, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sBlastRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.blastfurnace", "Blast Furnace", null, RES_PATH_GUI + "basicmachines/Default", 2, 2, 1, 0, 1, "Heat Capacity: ", 1, " K", false, true);
        public static final GT_Recipe_Map sPrimitiveBlastRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.primitiveblastfurnace", "Primitive Blast Furnace", null, RES_PATH_GUI + "basicmachines/Default", 3, 3, 1, 0, 1, E, 1, E, false, true);
        public static final GT_Recipe_Map sImplosionRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.implosioncompressor", "Implosion Compressor", null, RES_PATH_GUI + "basicmachines/Default", 2, 2, 2, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sVacuumRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.vacuumfreezer", "Vacuum Freezer", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sChemicalRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.chemicalreactor", "Chemical Reactor", null, RES_PATH_GUI + "basicmachines/ChemicalReactor", 2, 2, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sMultiblockChemicalRecipes = new GT_Recipe_Map_LargeChemicalReactor();
        public static final GT_Recipe_Map sDistillationRecipes = new GT_Recipe_Map_DistillationTower();
        public static final GT_Recipe_Map sCrakingRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.craker", "Oil Cracker", null, RES_PATH_GUI + "basicmachines/OilCracker", 1, 1, 1, 2, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sPyrolyseRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.pyro", "Pyrolyse Oven", null, RES_PATH_GUI + "basicmachines/Default", 2, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sWiremillRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.wiremill", "Wiremill", null, RES_PATH_GUI + "basicmachines/Wiremill", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sBenderRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.metalbender", "Bending Machine", null, RES_PATH_GUI + "basicmachines/Bender", 2, 1, 2, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sAlloySmelterRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.alloysmelter", "Alloy Smelter", null, RES_PATH_GUI + "basicmachines/AlloySmelter", 2, 1, 2, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sAssemblerRecipes = new GT_Recipe_Map_Assembler(new NonBlockingHashSet<>(), "gt.recipe.assembler", "Assembler", null, RES_PATH_GUI + "basicmachines/Assembler2", 9, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sCircuitAssemblerRecipes = new GT_Recipe_Map_Assembler(new NonBlockingHashSet<>(), "gt.recipe.circuitassembler", "Circuit Assembler", null, RES_PATH_GUI + "basicmachines/CircuitAssembler", 6, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sCannerRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.canner", "Canning Machine", null, RES_PATH_GUI + "basicmachines/Canner", 2, 2, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sCNCRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.cncmachine", "CNC Machine", null, RES_PATH_GUI + "basicmachines/Default", 2, 1, 2, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sLatheRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.lathe", "Lathe", null, RES_PATH_GUI + "basicmachines/Lathe", 1, 2, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sCutterRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.cuttingsaw", "Cutting Machine", null, RES_PATH_GUI + "basicmachines/Cutter2", 2, 2, 1, 1, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sSlicerRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.slicer", "Slicing Machine", null, RES_PATH_GUI + "basicmachines/Slicer", 2, 1, 2, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sExtruderRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.extruder", "Extruder", null, RES_PATH_GUI + "basicmachines/Extruder", 2, 1, 2, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sHammerRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.hammer", "Forge Hammer", null, RES_PATH_GUI + "basicmachines/Hammer", 1, 1, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sAmplifiers = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.uuamplifier", "Amplifabricator", null, RES_PATH_GUI + "basicmachines/Amplifabricator", 1, 0, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map sMassFabFakeRecipes = new GT_Recipe_Map(new NonBlockingHashSet<>(), "gt.recipe.massfab", "Mass Fabrication", null, RES_PATH_GUI + "basicmachines/Massfabricator", 1, 0, 1, 0, 1, E, 1, E, true, true);
        public static final GT_Recipe_Map_Fuel sDieselFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.dieselgeneratorfuel", "Diesel Generator Fuel", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sTurbineFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.gasturbinefuel", "Gas Turbine Fuel", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sHotFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.thermalgeneratorfuel", "Thermal Generator Fuel", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, false);
        public static final GT_Recipe_Map_Fuel sDenseLiquidFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.semifluidboilerfuels", "Semifluid Boiler Fuels", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sPlasmaFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.plasmageneratorfuels", "Plasma Generator Fuels", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sMagicFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.magicfuels", "Magic Energy Absorber", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sSmallNaquadahReactorFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.smallnaquadahreactor", "Naquadah Reactor MkI", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sLargeNaquadahReactorFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.largenaquadahreactor", "Naquadah Reactor MkII", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sHugeNaquadahReactorFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.fluidnaquadahreactor", "Naquadah Reactor MkIII", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sExtremeNaquadahReactorFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.hugenaquadahreactor", "Naquadah Reactor MkIV", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sUltraHugeNaquadahReactorFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.extrahugenaquadahreactor", "Naquadah Reactor MkV", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_Fuel sFluidNaquadahReactorFuels = new GT_Recipe_Map_Fuel(new NonBlockingHashSet<>(), "gt.recipe.fluidnaquadahreactor", "Fluid Naquadah Reactor", null, RES_PATH_GUI + "basicmachines/Default", 1, 1, 0, 0, 1, "Fuel Value: ", 1000, " EU", true, true);
        public static final GT_Recipe_Map_LargeBoilerFakeFuels sLargeBoilerFakeFuels = new GT_Recipe_Map_LargeBoilerFakeFuels();
        
        /**
         * HashMap of Recipes based on their Items
         */
        public final Map<GT_ItemStack, Collection<GT_Recipe>> mRecipeItemMap = new NonBlockingHashMap<>();
        /**
         * HashMap of Recipes based on their Fluids
         */
        public final Map<Fluid, Collection<GT_Recipe>> mRecipeFluidMap = new NonBlockingHashMap<>();
        public final Set<String> mRecipeFluidNameMap = new NonBlockingHashSet<>();
        /**
         * The List of all Recipes
         */
        public final Collection<GT_Recipe> mRecipeList;
        /**
         * String used as an unlocalised Name.
         */
        public final String mUnlocalizedName;
        /**
         * String used in NEI for the Recipe Lists. If null it will use the unlocalised Name instead
         */
        public final String mNEIName;
        /**
         * GUI used for NEI Display. Usually the GUI of the Machine itself
         */
        public final String mNEIGUIPath;
        public final String mNEISpecialValuePre, mNEISpecialValuePost;
        public final int mUsualInputCount, mUsualOutputCount, mNEISpecialValueMultiplier, mMinimalInputItems, mMinimalInputFluids, mAmperage;
        public final boolean mNEIAllowed, mShowVoltageAmperageInNEI;

        /**
         * Initialises a new type of Recipe Handler.
         *
         * @param aRecipeList                a List you specify as Recipe List. Usually just an ArrayList with a pre-initialised Size.
         * @param aUnlocalizedName           the unlocalised Name of this Recipe Handler, used mainly for NEI.
         * @param aLocalName                 the displayed Name inside the NEI Recipe GUI.
         * @param aNEIGUIPath                the displayed GUI Texture, usually just a Machine GUI. Auto-Attaches ".png" if forgotten.
         * @param aUsualInputCount           the usual amount of Input Slots this Recipe Class has.
         * @param aUsualOutputCount          the usual amount of Output Slots this Recipe Class has.
         * @param aNEISpecialValuePre        the String in front of the Special Value in NEI.
         * @param aNEISpecialValueMultiplier the Value the Special Value is getting Multiplied with before displaying
         * @param aNEISpecialValuePost       the String after the Special Value. Usually for a Unit or something.
         * @param aNEIAllowed                if NEI is allowed to display this Recipe Handler in general.
         */
        public GT_Recipe_Map(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            sMappings.add(this);
            mNEIAllowed = aNEIAllowed;
            mShowVoltageAmperageInNEI = aShowVoltageAmperageInNEI;
            mRecipeList = aRecipeList;
            mNEIName = aNEIName == null ? aUnlocalizedName : aNEIName;
            mNEIGUIPath = aNEIGUIPath.endsWith(".png") ? aNEIGUIPath : aNEIGUIPath + ".png";
            mNEISpecialValuePre = aNEISpecialValuePre;
            mNEISpecialValueMultiplier = aNEISpecialValueMultiplier;
            mNEISpecialValuePost = aNEISpecialValuePost;
            mAmperage = aAmperage;
            mUsualInputCount = aUsualInputCount;
            mUsualOutputCount = aUsualOutputCount;
            mMinimalInputItems = aMinimalInputItems;
            mMinimalInputFluids = aMinimalInputFluids;
            GregTech_API.sFluidMappings.add(mRecipeFluidMap);
            GregTech_API.sItemStackMappings.add(mRecipeItemMap);
            GT_LanguageManager.addStringLocalization(mUnlocalizedName = aUnlocalizedName, aLocalName);
        }

        public GT_Recipe addRecipe(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, int[] aOutputChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return addRecipe(new GT_Recipe(aOptimize, aInputs, aOutputs, aSpecial, aOutputChances, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue));
        }

        public GT_Recipe addRecipe(int[] aOutputChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return addRecipe(new GT_Recipe(false, null, null, null, aOutputChances, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue), false, false, false);
        }

        public GT_Recipe addRecipe(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return addRecipe(new GT_Recipe(aOptimize, aInputs, aOutputs, aSpecial, null, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue));
        }

        public GT_Recipe addRecipe(GT_Recipe aRecipe) {
            return addRecipe(aRecipe, true, false, false);
        }

        protected GT_Recipe addRecipe(GT_Recipe aRecipe, boolean aCheckForCollisions, boolean aFakeRecipe, boolean aHidden) {
            aRecipe.mHidden = aHidden;
            aRecipe.mFakeRecipe = aFakeRecipe;
            if (aRecipe.mFluidInputs.length < mMinimalInputFluids && aRecipe.mInputs.length < mMinimalInputItems)
                return null;
            if (aCheckForCollisions && findRecipe(null, false, Long.MAX_VALUE, aRecipe.mFluidInputs, aRecipe.mInputs) != null)
                return null;
            return add(aRecipe);
        }

        /**
         * Only used for fake Recipe Handlers to show something in NEI, do not use this for adding actual Recipes! findRecipe wont find fake Recipes, containsInput WILL find fake Recipes
         */
        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, int[] aOutputChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return addFakeRecipe(aCheckForCollisions, new GT_Recipe(false, aInputs, aOutputs, aSpecial, aOutputChances, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue));
        }

        /**
         * Only used for fake Recipe Handlers to show something in NEI, do not use this for adding actual Recipes! findRecipe wont find fake Recipes, containsInput WILL find fake Recipes
         */
        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return addFakeRecipe(aCheckForCollisions, new GT_Recipe(false, aInputs, aOutputs, aSpecial, null, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue));
        }
        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue,boolean hidden) {
            return addFakeRecipe(aCheckForCollisions, new GT_Recipe(false, aInputs, aOutputs, aSpecial, null, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue),hidden);
        }

        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue, ItemStack[][] aAlt ,boolean hidden) {
            return addFakeRecipe(aCheckForCollisions, new GT_Recipe_WithAlt(false, aInputs, aOutputs, aSpecial, null, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue, aAlt),hidden);
        }

        /**
         * Only used for fake Recipe Handlers to show something in NEI, do not use this for adding actual Recipes! findRecipe wont find fake Recipes, containsInput WILL find fake Recipes
         */
        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, GT_Recipe aRecipe) {
            return addRecipe(aRecipe, aCheckForCollisions, true, false);
        }
        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, GT_Recipe aRecipe,boolean hidden) {
            return addRecipe(aRecipe, aCheckForCollisions, true, hidden);
        }

        public GT_Recipe add(GT_Recipe aRecipe) {
            mRecipeList.add(aRecipe);
            for (FluidStack aFluid : aRecipe.mFluidInputs)
                if (aFluid != null) {
                    Collection<GT_Recipe> tList = mRecipeFluidMap.get(aFluid.getFluid());
                    if (tList == null) mRecipeFluidMap.put(aFluid.getFluid(), tList = new NonBlockingHashSet<>());
                    tList.add(aRecipe);
                    if(!mRecipeFluidNameMap.contains(aFluid.getFluid().getName()))
                    	mRecipeFluidNameMap.add(aFluid.getFluid().getName());
                }
            return addToItemMap(aRecipe);
        }

        public void reInit() {
            Map<GT_ItemStack, Collection<GT_Recipe>> tMap = mRecipeItemMap;
            if (tMap != null) tMap.clear();
            for (GT_Recipe tRecipe : mRecipeList) {
                GT_OreDictUnificator.setStackArray(true, tRecipe.mInputs);
                GT_OreDictUnificator.setStackArray(true, tRecipe.mOutputs);
                if (tMap != null) addToItemMap(tRecipe);
            }
        }

        /**
         * @return if this Item is a valid Input for any for the Recipes
         */
        public boolean containsInput(ItemStack aStack) {
            return aStack != null && (mRecipeItemMap.containsKey(new GT_ItemStack(aStack)) || mRecipeItemMap.containsKey(new GT_ItemStack(GT_Utility.copyMetaData(W, aStack))));
        }

        /**
         * @return if this Fluid is a valid Input for any for the Recipes
         */
        public boolean containsInput(FluidStack aFluid) {
            return aFluid != null && containsInput(aFluid.getFluid());
        }

        /**
         * @return if this Fluid is a valid Input for any for the Recipes
         */
        public boolean containsInput(Fluid aFluid) {
        	return aFluid != null && mRecipeFluidNameMap.contains(aFluid.getName());
        }

        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack... aInputs) {
            return findRecipe(aTileEntity, null, aNotUnificated, aVoltage, aFluids, null, aInputs);
        }

        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, boolean aNotUnificated, boolean aDontCheckStackSizes, long aVoltage, FluidStack[] aFluids, ItemStack... aInputs) {
            return findRecipe(aTileEntity, null, aNotUnificated, aDontCheckStackSizes, aVoltage, aFluids, null, aInputs);
        }

        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack... aInputs) {
            return findRecipe(aTileEntity, aRecipe, aNotUnificated, aVoltage, aFluids, null, aInputs);
        }

        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, boolean aDontCheckStackSizes, long aVoltage, FluidStack[] aFluids, ItemStack... aInputs) {
            return findRecipe(aTileEntity, aRecipe, aNotUnificated, aDontCheckStackSizes, aVoltage, aFluids, null, aInputs);
        }

        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
        	return findRecipe(aTileEntity, aRecipe, aNotUnificated, true, aVoltage, aFluids, aSpecialSlot, aInputs);
        }	
        /**
         * finds a Recipe matching the aFluid and ItemStack Inputs.
         *
         * @param aTileEntity    an Object representing the current coordinates of the executing Block/Entity/Whatever. This may be null, especially during Startup.
         * @param aRecipe        in case this is != null it will try to use this Recipe first when looking things up.
         * @param aNotUnificated if this is T the Recipe searcher will unificate the ItemStack Inputs
         * @param aDontCheckStackSizes if set to false will only return recipes that can be executed at least once with the provided input
         * @param aVoltage       Voltage of the Machine or Long.MAX_VALUE if it has no Voltage
         * @param aFluids        the Fluid Inputs
         * @param aSpecialSlot   the content of the Special Slot, the regular Manager doesn't do anything with this, but some custom ones do.
         * @param aInputs        the Item Inputs
         * @return the Recipe it has found or null for no matching Recipe
         */
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, boolean aDontCheckStackSizes, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            // No Recipes? Well, nothing to be found then.
            if (mRecipeList.isEmpty()) return null;

            // Some Recipe Classes require a certain amount of Inputs of certain kinds. Like "at least 1 Fluid + 1 Stack" or "at least 2 Stacks" before they start searching for Recipes.
            // This improves Performance massively, especially if people leave things like Circuits, Molds or Shapes in their Machines to select Sub Recipes.
            if (GregTech_API.sPostloadFinished) {
                if (mMinimalInputFluids > 0) {
                    if (aFluids == null) return null;
                    int tAmount = 0;
                    for (FluidStack aFluid : aFluids) if (aFluid != null) tAmount++;
                    if (tAmount < mMinimalInputFluids) return null;
                }
                if (mMinimalInputItems > 0) {
                    if (aInputs == null) return null;
                    int tAmount = 0;
                    for (ItemStack aInput : aInputs) if (aInput != null) tAmount++;
                    if (tAmount < mMinimalInputItems) return null;
                }
            }

            // Unification happens here in case the Input isn't already unificated.
            if (aNotUnificated) aInputs = GT_OreDictUnificator.getStackArray(true, (Object[]) aInputs);

            // Check the Recipe which has been used last time in order to not have to search for it again, if possible.
            if (aRecipe != null)
                if (!aRecipe.mFakeRecipe && aRecipe.mCanBeBuffered && aRecipe.isRecipeInputEqual(false, aDontCheckStackSizes, aFluids, aInputs))
                    return aRecipe.mEnabled && aVoltage * mAmperage >= aRecipe.mEUt ? aRecipe : null;

            // Now look for the Recipes inside the Item HashMaps, but only when the Recipes usually have Items.
            if (mUsualInputCount > 0 && aInputs != null) for (ItemStack tStack : aInputs)
                if (tStack != null) {
                    Collection<GT_Recipe>
                            tRecipes = mRecipeItemMap.get(new GT_ItemStack(tStack));
                    if (tRecipes != null) for (GT_Recipe tRecipe : tRecipes)
                        if (!tRecipe.mFakeRecipe && tRecipe.isRecipeInputEqual(false, aDontCheckStackSizes, aFluids, aInputs))
                            return tRecipe.mEnabled && aVoltage * mAmperage >= tRecipe.mEUt ? tRecipe : null;
                    tRecipes = mRecipeItemMap.get(new GT_ItemStack(GT_Utility.copyMetaData(W, tStack)));
                    if (tRecipes != null) for (GT_Recipe tRecipe : tRecipes)
                        if (!tRecipe.mFakeRecipe && tRecipe.isRecipeInputEqual(false, aDontCheckStackSizes, aFluids, aInputs))
                            return tRecipe.mEnabled && aVoltage * mAmperage >= tRecipe.mEUt ? tRecipe : null;
                }

            // If the minimal Amount of Items for the Recipe is 0, then it could be a Fluid-Only Recipe, so check that Map too.
            if (mMinimalInputItems == 0 && aFluids != null) for (FluidStack aFluid : aFluids)
                if (aFluid != null) {
                    Collection<GT_Recipe>
                            tRecipes = mRecipeFluidMap.get(aFluid.getFluid());
                    if (tRecipes != null) for (GT_Recipe tRecipe : tRecipes)
                        if (!tRecipe.mFakeRecipe && tRecipe.isRecipeInputEqual(false, aDontCheckStackSizes, aFluids, aInputs))
                            return tRecipe.mEnabled && aVoltage * mAmperage >= tRecipe.mEUt ? tRecipe : null;
                }

            // And nothing has been found.
            return null;
        }

        protected GT_Recipe addToItemMap(GT_Recipe aRecipe) {
            for (ItemStack aStack : aRecipe.mInputs)
                if (aStack != null) {
                    GT_ItemStack tStack = new GT_ItemStack(aStack);
                    Collection<GT_Recipe> tList = mRecipeItemMap.get(tStack);
                    if (tList == null) mRecipeItemMap.put(tStack, tList = new NonBlockingHashSet<>());
                    tList.add(aRecipe);
                }
            return aRecipe;
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Here are a few Classes I use for Special Cases in some Machines without having to write a separate Machine Class.
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Abstract Class for general Recipe Handling of non GT Recipes
     */
    public static abstract class GT_Recipe_Map_NonGTRecipes extends GT_Recipe_Map {
        public GT_Recipe_Map_NonGTRecipes(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return false;
        }

        @Override
        public boolean containsInput(FluidStack aFluid) {
            return false;
        }

        @Override
        public boolean containsInput(Fluid aFluid) {
            return false;
        }

        @Override
        public GT_Recipe addRecipe(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, int[] aOutputChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return null;
        }

        @Override
        public GT_Recipe addRecipe(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return null;
        }

        @Override
        public GT_Recipe addRecipe(GT_Recipe aRecipe) {
            return null;
        }

        @Override
        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, int[] aOutputChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return null;
        }

        @Override
        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return null;
        }

        @Override
        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue,boolean hidden) {
            return null;
        }

        @Override
        public GT_Recipe addFakeRecipe(boolean aCheckForCollisions, GT_Recipe aRecipe) {
            return null;
        }

        @Override
        public GT_Recipe add(GT_Recipe aRecipe) {
            return null;
        }

        @Override
        public void reInit() {/**/}

        @Override
        protected GT_Recipe addToItemMap(GT_Recipe aRecipe) {
            return null;
        }
    }

    /**
     * Just a Recipe Map with Utility specifically for Fuels.
     */
    public static class GT_Recipe_Map_Fuel extends GT_Recipe_Map {
        public GT_Recipe_Map_Fuel(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        public GT_Recipe addFuel(ItemStack aInput, ItemStack aOutput, int aFuelValueInEU) {
            return addFuel(aInput, aOutput, null, null, 10000, aFuelValueInEU);
        }

        public GT_Recipe addFuel(ItemStack aInput, ItemStack aOutput, int aChance, int aFuelValueInEU) {
            return addFuel(aInput, aOutput, null, null, aChance, aFuelValueInEU);
        }

        public GT_Recipe addFuel(FluidStack aFluidInput, FluidStack aFluidOutput, int aFuelValueInEU) {
            return addFuel(null, null, aFluidInput, aFluidOutput, 10000, aFuelValueInEU);
        }

        public GT_Recipe addFuel(ItemStack aInput, ItemStack aOutput, FluidStack aFluidInput, FluidStack aFluidOutput, int aFuelValueInEU) {
            return addFuel(aInput, aOutput, aFluidInput, aFluidOutput, 10000, aFuelValueInEU);
        }

        public GT_Recipe addFuel(ItemStack aInput, ItemStack aOutput, FluidStack aFluidInput, FluidStack aFluidOutput, int aChance, int aFuelValueInEU) {
            return addRecipe(true, new ItemStack[]{aInput}, new ItemStack[]{aOutput}, null, new int[]{aChance}, new FluidStack[]{aFluidInput}, new FluidStack[]{aFluidOutput}, 0, 0, aFuelValueInEU);
        }
    }

    /**
     * Special Class for Furnace Recipe handling.
     */
    public static class GT_Recipe_Map_Furnace extends GT_Recipe_Map_NonGTRecipes {
        public GT_Recipe_Map_Furnace(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null) return null;
            if (aRecipe != null && aRecipe.isRecipeInputEqual(false, true, aFluids, aInputs)) return aRecipe;
            ItemStack tOutput = GT_ModHandler.getSmeltingOutput(aInputs[0], false, null);
            return tOutput == null ? null : new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(1, aInputs[0])}, new ItemStack[]{tOutput}, null, null, null, null, 128, 4, 0);
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return GT_ModHandler.getSmeltingOutput(aStack, false, null) != null;
        }
    }

    /**
     * Special Class for Microwave Recipe handling.
     */
    public static class GT_Recipe_Map_Microwave extends GT_Recipe_Map_NonGTRecipes {
        public GT_Recipe_Map_Microwave(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null) return null;
            if (aRecipe != null && aRecipe.isRecipeInputEqual(false, true, aFluids, aInputs)) return aRecipe;
            ItemStack tOutput = GT_ModHandler.getSmeltingOutput(aInputs[0], false, null);

            if (GT_Utility.areStacksEqual(aInputs[0], new ItemStack(Items.book, 1, W))) {
                return new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(1, aInputs[0])}, new ItemStack[]{GT_Utility.getWrittenBook("Manual_Microwave", ItemList.Book_Written_03.get(1))}, null, null, null, null, 32, 4, 0);
            }

            // Check Container Item of Input since it is around the Input, then the Input itself, then Container Item of Output and last check the Output itself
            for (ItemStack tStack : new ItemStack[]{GT_Utility.getContainerItem(aInputs[0], true), aInputs[0], GT_Utility.getContainerItem(tOutput, true), tOutput})
                if (tStack != null) {
                    if (GT_Utility.areStacksEqual(tStack, new ItemStack(Blocks.netherrack, 1, W), true)
                            || GT_Utility.areStacksEqual(tStack, new ItemStack(Blocks.tnt, 1, W), true)
                            || GT_Utility.areStacksEqual(tStack, new ItemStack(Items.egg, 1, W), true)
                            || GT_Utility.areStacksEqual(tStack, new ItemStack(Items.firework_charge, 1, W), true)
                            || GT_Utility.areStacksEqual(tStack, new ItemStack(Items.fireworks, 1, W), true)
                            || GT_Utility.areStacksEqual(tStack, new ItemStack(Items.fire_charge, 1, W), true)
                            ) {
                        if (aTileEntity instanceof IGregTechTileEntity) {
                            GT_Log.exp.println("Microwave Explosion due to TNT || EGG || FIREWORKCHARGE || FIREWORK || FIRE CHARGE");
                            ((IGregTechTileEntity) aTileEntity).doExplosion(aVoltage * 4);
                        }
                        return null;
                    }
                    ItemData tData = GT_OreDictUnificator.getItemData(tStack);


                    if (tData != null) {
                        if (tData.mMaterial != null && tData.mMaterial.mMaterial != null) {
                            if (tData.mMaterial.mMaterial.contains(SubTag.METAL) || tData.mMaterial.mMaterial.contains(SubTag.EXPLOSIVE)) {
                                if (aTileEntity instanceof IGregTechTileEntity) {
                                    GT_Log.exp.println("Microwave Explosion due to METAL insertion");
                                    ((IGregTechTileEntity) aTileEntity).doExplosion(aVoltage * 4);
                                }
                                return null;
                            }
                            if (tData.mMaterial.mMaterial.contains(SubTag.FLAMMABLE)) {
                                if (aTileEntity instanceof IGregTechTileEntity) {
                                    GT_Log.exp.println("Microwave INFLAMMATION due to FLAMMABLE insertion");
                                    ((IGregTechTileEntity) aTileEntity).setOnFire();
                                }
                                return null;
                            }
                        }
                        for (MaterialStack tMaterial : tData.mByProducts)
                            if (tMaterial != null) {
                                if (tMaterial.mMaterial.contains(SubTag.METAL) || tMaterial.mMaterial.contains(SubTag.EXPLOSIVE)) {
                                    if (aTileEntity instanceof IGregTechTileEntity) {
                                        GT_Log.exp.println("Microwave Explosion due to METAL insertion");
                                        ((IGregTechTileEntity) aTileEntity).doExplosion(aVoltage * 4);
                                    }
                                    return null;
                                }
                                if (tMaterial.mMaterial.contains(SubTag.FLAMMABLE)) {
                                    if (aTileEntity instanceof IGregTechTileEntity) {
                                        ((IGregTechTileEntity) aTileEntity).setOnFire();
                                        GT_Log.exp.println("Microwave INFLAMMATION due to FLAMMABLE insertion");
                                    }
                                    return null;
                                }
                            }
                    }
                    if (TileEntityFurnace.getItemBurnTime(tStack) > 0) {
                        if (aTileEntity instanceof IGregTechTileEntity) {
                            ((IGregTechTileEntity) aTileEntity).setOnFire();
                            GT_Log.exp.println("Microwave INFLAMMATION due to BURNABLE insertion");
                        }
                        return null;
                    }

                }

            return tOutput == null ? null : new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(1, aInputs[0])}, new ItemStack[]{tOutput}, null, null, null, null, 32, 4, 0);
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return GT_ModHandler.getSmeltingOutput(aStack, false, null) != null;
        }
    }

    /**
     * Special Class for Unboxinator handling.
     */
    public static class GT_Recipe_Map_Unboxinator extends GT_Recipe_Map {
        public GT_Recipe_Map_Unboxinator(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            if (aInputs == null || aInputs.length <= 0 || !ItemList.IC2_Scrapbox.isStackEqual(aInputs[0], false, true))
                return super.findRecipe(aTileEntity, aRecipe, aNotUnificated, aVoltage, aFluids, aSpecialSlot, aInputs);
            ItemStack tOutput = GT_ModHandler.getRandomScrapboxDrop();
            if (tOutput == null)
                return super.findRecipe(aTileEntity, aRecipe, aNotUnificated, aVoltage, aFluids, aSpecialSlot, aInputs);
            GT_Recipe rRecipe = new GT_Recipe(false, new ItemStack[]{ItemList.IC2_Scrapbox.get(1)}, new ItemStack[]{tOutput}, null, null, null, null, 16, 1, 0);
            // It is not allowed to be buffered due to the random Output
            rRecipe.mCanBeBuffered = false;
            // Due to its randomness it is not good if there are Items in the Output Slot, because those Items could manipulate the outcome.
            rRecipe.mNeedsEmptyOutput = true;
            return rRecipe;
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return ItemList.IC2_Scrapbox.isStackEqual(aStack, false, true) || super.containsInput(aStack);
        }
    }

    /**
     * Special Class for Fluid Canner handling.
     */
    public static class GT_Recipe_Map_FluidCanner extends GT_Recipe_Map {
        public GT_Recipe_Map_FluidCanner(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            GT_Recipe rRecipe = super.findRecipe(aTileEntity, aRecipe, aNotUnificated, aVoltage, aFluids, aSpecialSlot, aInputs);
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null || rRecipe != null || !GregTech_API.sPostloadFinished)
                return rRecipe;
            if (aFluids != null && aFluids.length > 0 && aFluids[0] != null) {
                ItemStack tOutput = GT_Utility.fillFluidContainer(aFluids[0], aInputs[0], false, true);
                FluidStack tFluid = GT_Utility.getFluidForFilledItem(tOutput, true);
                if (tFluid != null)
                    rRecipe = new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(1, aInputs[0])}, new ItemStack[]{tOutput}, null, null, new FluidStack[]{tFluid}, null, Math.max(tFluid.amount / 64, 16), 1, 0);
            }
            if (rRecipe == null) {
                FluidStack tFluid = GT_Utility.getFluidForFilledItem(aInputs[0], true);
                if (tFluid != null)
                    rRecipe = new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(1, aInputs[0])}, new ItemStack[]{GT_Utility.getContainerItem(aInputs[0], true)}, null, null, null, new FluidStack[]{tFluid}, Math.max(tFluid.amount / 64, 16), 1, 0);
            }
            if (rRecipe != null) rRecipe.mCanBeBuffered = false;
            return rRecipe;
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return aStack != null && (super.containsInput(aStack) || (aStack.getItem() instanceof IFluidContainerItem && ((IFluidContainerItem) aStack.getItem()).getCapacity(aStack) > 0));
        }

        @Override
        public boolean containsInput(FluidStack aFluid) {
            return true;
        }

        @Override
        public boolean containsInput(Fluid aFluid) {
            return true;
        }
    }

    /**
     * Special Class for Recycler Recipe handling.
     */
    public static class GT_Recipe_Map_Recycler extends GT_Recipe_Map_NonGTRecipes {
        public GT_Recipe_Map_Recycler(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null) return null;
            if (aRecipe != null && aRecipe.isRecipeInputEqual(false, true, aFluids, aInputs)) return aRecipe;
            return new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(1, aInputs[0])}, GT_ModHandler.getRecyclerOutput(GT_Utility.copyAmount(64, aInputs[0]), 0) == null ? null : new ItemStack[]{ItemList.IC2_Scrap.get(1)}, null, new int[]{1250}, null, null, 45, 1, 0);
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return GT_ModHandler.getRecyclerOutput(GT_Utility.copyAmount(64, aStack), 0) != null;
        }
    }

    /**
     * Special Class for Compressor Recipe handling.
     */
    public static class GT_Recipe_Map_Compressor extends GT_Recipe_Map_NonGTRecipes {
        public GT_Recipe_Map_Compressor(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null) return null;
            if (aRecipe != null && aRecipe.isRecipeInputEqual(false, true, aFluids, aInputs)) return aRecipe;
            ItemStack tComparedInput = GT_Utility.copy(aInputs[0]);
            ItemStack[] tOutputItems = GT_ModHandler.getMachineOutput(tComparedInput, ic2.api.recipe.Recipes.compressor.getRecipes(), true, new NBTTagCompound(), null, null, null);
            return GT_Utility.arrayContainsNonNull(tOutputItems) ? new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(aInputs[0].stackSize - tComparedInput.stackSize, aInputs[0])}, tOutputItems, null, null, null, null, 400, 2, 0) : null;
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return GT_Utility.arrayContainsNonNull(GT_ModHandler.getMachineOutput(GT_Utility.copyAmount(64, aStack), ic2.api.recipe.Recipes.compressor.getRecipes(), false, new NBTTagCompound(), null, null, null));
        }
    }

    /**
     * Special Class for Extractor Recipe handling.
     */
    public static class GT_Recipe_Map_Extractor extends GT_Recipe_Map_NonGTRecipes {
        public GT_Recipe_Map_Extractor(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null) return null;
            if (aRecipe != null && aRecipe.isRecipeInputEqual(false, true, aFluids, aInputs)) return aRecipe;
            ItemStack tComparedInput = GT_Utility.copy(aInputs[0]);
            ItemStack[] tOutputItems = GT_ModHandler.getMachineOutput(tComparedInput, ic2.api.recipe.Recipes.extractor.getRecipes(), true, new NBTTagCompound(), null, null, null);
            return GT_Utility.arrayContainsNonNull(tOutputItems) ? new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(aInputs[0].stackSize - tComparedInput.stackSize, aInputs[0])}, tOutputItems, null, null, null, null, 400, 2, 0) : null;
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return GT_Utility.arrayContainsNonNull(GT_ModHandler.getMachineOutput(GT_Utility.copyAmount(64, aStack), ic2.api.recipe.Recipes.extractor.getRecipes(), false, new NBTTagCompound(), null, null, null));
        }
    }

    /**
     * Special Class for Thermal Centrifuge Recipe handling.
     */
    public static class GT_Recipe_Map_ThermalCentrifuge extends GT_Recipe_Map_NonGTRecipes {
        public GT_Recipe_Map_ThermalCentrifuge(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null) return null;
            if (aRecipe != null && aRecipe.isRecipeInputEqual(false, true, aFluids, aInputs)) return aRecipe;
            ItemStack tComparedInput = GT_Utility.copy(aInputs[0]);
            ItemStack[] tOutputItems = GT_ModHandler.getMachineOutput(tComparedInput, ic2.api.recipe.Recipes.centrifuge.getRecipes(), true, new NBTTagCompound(), null, null, null);
            return GT_Utility.arrayContainsNonNull(tOutputItems) ? new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(aInputs[0].stackSize - tComparedInput.stackSize, aInputs[0])}, tOutputItems, null, null, null, null, 400, 48, 0) : null;
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return GT_Utility.arrayContainsNonNull(GT_ModHandler.getMachineOutput(GT_Utility.copyAmount(64, aStack), ic2.api.recipe.Recipes.centrifuge.getRecipes(), false, new NBTTagCompound(), null, null, null));
        }
    }

    /**
     * Special Class for Ore Washer Recipe handling.
     */
    public static class GT_Recipe_Map_OreWasher extends GT_Recipe_Map_NonGTRecipes {
        public GT_Recipe_Map_OreWasher(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null || aFluids == null || aFluids.length < 1 || !GT_ModHandler.isWater(aFluids[0]))
                return null;
            if (aRecipe != null && aRecipe.isRecipeInputEqual(false, true, aFluids, aInputs)) return aRecipe;
            ItemStack tComparedInput = GT_Utility.copy(aInputs[0]);
            NBTTagCompound aRecipeMetaData = new NBTTagCompound();
            ItemStack[] tOutputItems = GT_ModHandler.getMachineOutput(tComparedInput, ic2.api.recipe.Recipes.oreWashing.getRecipes(), true, aRecipeMetaData, null, null, null);
            return GT_Utility.arrayContainsNonNull(tOutputItems) ? new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(aInputs[0].stackSize - tComparedInput.stackSize, aInputs[0])}, tOutputItems, null, null, new FluidStack[]{new FluidStack(aFluids[0].getFluid(), ((NBTTagCompound) aRecipeMetaData.getTag("return")).getInteger("amount"))}, null, 400, 16, 0) : null;
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return GT_Utility.arrayContainsNonNull(GT_ModHandler.getMachineOutput(GT_Utility.copyAmount(64, aStack), ic2.api.recipe.Recipes.oreWashing.getRecipes(), false, new NBTTagCompound(), null, null, null));
        }

        @Override
        public boolean containsInput(FluidStack aFluid) {
            return GT_ModHandler.isWater(aFluid);
        }

        @Override
        public boolean containsInput(Fluid aFluid) {
            return GT_ModHandler.isWater(new FluidStack(aFluid, 0));
        }
    }

    /**
     * Special Class for Macerator/RockCrusher Recipe handling.
     */
    public static class GT_Recipe_Map_Macerator extends GT_Recipe_Map {
        public GT_Recipe_Map_Macerator(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null || !GregTech_API.sPostloadFinished)
                return super.findRecipe(aTileEntity, aRecipe, aNotUnificated, aVoltage, aFluids, aSpecialSlot, aInputs);
            aRecipe = super.findRecipe(aTileEntity, aRecipe, aNotUnificated, aVoltage, aFluids, aSpecialSlot, aInputs);
            if (aRecipe != null) return aRecipe;

            try {
                List<ItemStack> tRecipeOutputs = mods.railcraft.api.crafting.RailcraftCraftingManager.rockCrusher.getRecipe(GT_Utility.copyAmount(1, aInputs[0])).getRandomizedOuputs();
                if (tRecipeOutputs != null) {
                    aRecipe = new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(1, aInputs[0])}, tRecipeOutputs.toArray(new ItemStack[tRecipeOutputs.size()]), null, null, null, null, 800, 2, 0);
                    aRecipe.mCanBeBuffered = false;
                    aRecipe.mNeedsEmptyOutput = true;
                    return aRecipe;
                }
            } catch (NoClassDefFoundError e) {
                if (D1) GT_Log.err.println("Railcraft Not loaded");
            } catch (NullPointerException e) {/**/}

            ItemStack tComparedInput = GT_Utility.copy(aInputs[0]);
            ItemStack[] tOutputItems = GT_ModHandler.getMachineOutput(tComparedInput, ic2.api.recipe.Recipes.macerator.getRecipes(), true, new NBTTagCompound(), null, null, null);
            return GT_Utility.arrayContainsNonNull(tOutputItems) ? new GT_Recipe(false, new ItemStack[]{GT_Utility.copyAmount(aInputs[0].stackSize - tComparedInput.stackSize, aInputs[0])}, tOutputItems, null, null, null, null, 400, 2, 0) : null;
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return super.containsInput(aStack) || GT_Utility.arrayContainsNonNull(GT_ModHandler.getMachineOutput(GT_Utility.copyAmount(64, aStack), ic2.api.recipe.Recipes.macerator.getRecipes(), false, new NBTTagCompound(), null, null, null));
        }
    }

    /**
     * Special Class for Assembler handling.
     */
    public static class GT_Recipe_Map_Assembler extends GT_Recipe_Map {

        public GT_Recipe_Map_Assembler(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {

            GT_Recipe rRecipe = super.findRecipe(aTileEntity, aRecipe, true, aVoltage, aFluids, aSpecialSlot, aInputs);
/*


            Doesnt work, keep it as a reminder tho

            if (rRecipe == null){
                Set<ItemStack> aInputs2 = new TreeSet<ItemStack>();
                for (ItemStack aInput : aInputs) {
                    aInputs2.add(aInput);
                }

                for (ItemStack aInput : aInputs) {
                    aInputs2.remove(aInput);
                    int[] oredictIDs = OreDictionary.getOreIDs(aInput);
                    if ( oredictIDs.length > 1){
                        for (final int i : oredictIDs){
                            final ItemStack[] oredictIS = (ItemStack[]) OreDictionary.getOres(OreDictionary.getOreName(i)).toArray();
                            if (oredictIS != null && oredictIS.length > 1){
                                for (final ItemStack IS : oredictIS){
                                aInputs2.add(IS);
                                ItemStack[] temp = (ItemStack[]) aInputs2.toArray();
                                rRecipe = super.findRecipe(aTileEntity, aRecipe, aNotUnificated, aVoltage, aFluids, aSpecialSlot,temp);
                                    if(rRecipe!= null){
                                        break;
                                    }
                                else {
                                        aInputs2.remove(IS);
                                    }
                                }
                                if(rRecipe!= null)
                                break;
                            }
                        }
                        if(rRecipe!= null)
                            break;
                    }else
                        aInputs2.add(aInput);
                    if(rRecipe!= null)
                        break;
                }
            }
*/
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null || rRecipe == null || !GregTech_API.sPostloadFinished)
                return rRecipe;

            for (ItemStack aInput : aInputs) {
                if (ItemList.Paper_Printed_Pages.isStackEqual(aInput, false, true)) {
                    rRecipe = rRecipe.copy();
                    rRecipe.mCanBeBuffered = false;
                    rRecipe.mOutputs[0].setTagCompound(aInput.getTagCompound());
                }

            }
            return rRecipe;
        }

    }

    /**
     * Special Class for Forming Press handling.
     */
    public static class GT_Recipe_Map_FormingPress extends GT_Recipe_Map {
        public GT_Recipe_Map_FormingPress(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            GT_Recipe rRecipe = super.findRecipe(aTileEntity, aRecipe, aNotUnificated, aVoltage, aFluids, aSpecialSlot, aInputs);
            if (aInputs == null || aInputs.length < 2 || aInputs[0] == null || aInputs[1] == null || !GregTech_API.sPostloadFinished)
                return rRecipe;
            if (rRecipe == null) {
                if (ItemList.Shape_Mold_Name.isStackEqual(aInputs[0], false, true)) {
                    ItemStack tOutput = GT_Utility.copyAmount(1, aInputs[1]);
                    tOutput.setStackDisplayName(aInputs[0].getDisplayName());
                    rRecipe = new GT_Recipe(false, new ItemStack[]{ItemList.Shape_Mold_Name.get(0), GT_Utility.copyAmount(1, aInputs[1])}, new ItemStack[]{tOutput}, null, null, null, null, 128, 8, 0);
                    rRecipe.mCanBeBuffered = false;
                    return rRecipe;
                }
                if (ItemList.Shape_Mold_Name.isStackEqual(aInputs[1], false, true)) {
                    ItemStack tOutput = GT_Utility.copyAmount(1, aInputs[0]);
                    tOutput.setStackDisplayName(aInputs[1].getDisplayName());
                    rRecipe = new GT_Recipe(false, new ItemStack[]{ItemList.Shape_Mold_Name.get(0), GT_Utility.copyAmount(1, aInputs[0])}, new ItemStack[]{tOutput}, null, null, null, null, 128, 8, 0);
                    rRecipe.mCanBeBuffered = false;
                    return rRecipe;
                }
                return null;
            }
            for (ItemStack aMold : aInputs) {
                if (ItemList.Shape_Mold_Credit.isStackEqual(aMold, false, true)) {
                    NBTTagCompound tNBT = aMold.getTagCompound();
                    if (tNBT == null) tNBT = new NBTTagCompound();
                    if (!tNBT.hasKey("credit_security_id")) tNBT.setLong("credit_security_id", System.nanoTime());
                    aMold.setTagCompound(tNBT);

                    rRecipe = rRecipe.copy();
                    rRecipe.mCanBeBuffered = false;
                    rRecipe.mOutputs[0].setTagCompound(tNBT);
                    return rRecipe;
                }
            }
            return rRecipe;
        }
    }

    /**
     * Special Class for Printer handling.
     */
    public static class GT_Recipe_Map_Printer extends GT_Recipe_Map {
        public GT_Recipe_Map_Printer(Collection<GT_Recipe> aRecipeList, String aUnlocalizedName, String aLocalName, String aNEIName, String aNEIGUIPath, int aUsualInputCount, int aUsualOutputCount, int aMinimalInputItems, int aMinimalInputFluids, int aAmperage, String aNEISpecialValuePre, int aNEISpecialValueMultiplier, String aNEISpecialValuePost, boolean aShowVoltageAmperageInNEI, boolean aNEIAllowed) {
            super(aRecipeList, aUnlocalizedName, aLocalName, aNEIName, aNEIGUIPath, aUsualInputCount, aUsualOutputCount, aMinimalInputItems, aMinimalInputFluids, aAmperage, aNEISpecialValuePre, aNEISpecialValueMultiplier, aNEISpecialValuePost, aShowVoltageAmperageInNEI, aNEIAllowed);
        }

        @Override
        public GT_Recipe findRecipe(IHasWorldObjectAndCoords aTileEntity, GT_Recipe aRecipe, boolean aNotUnificated, long aVoltage, FluidStack[] aFluids, ItemStack aSpecialSlot, ItemStack... aInputs) {
            GT_Recipe rRecipe = super.findRecipe(aTileEntity, aRecipe, aNotUnificated, aVoltage, aFluids, aSpecialSlot, aInputs);
            if (aInputs == null || aInputs.length <= 0 || aInputs[0] == null || aFluids == null || aFluids.length <= 0 || aFluids[0] == null || !GregTech_API.sPostloadFinished)
                return rRecipe;

            Dyes aDye = null;
            for (Dyes tDye : Dyes.VALUES)
                if (tDye.isFluidDye(aFluids[0])) {
                    aDye = tDye;
                    break;
                }

            if (aDye == null) return rRecipe;

            if (rRecipe == null) {
                ItemStack
                        tOutput = GT_ModHandler.getAllRecipeOutput(aTileEntity == null ? null : aTileEntity.getWorld(), aInputs[0], aInputs[0], aInputs[0], aInputs[0], ItemList.DYE_ONLY_ITEMS[aDye.mIndex].get(1), aInputs[0], aInputs[0], aInputs[0], aInputs[0]);
                if (tOutput != null)
                    return addRecipe(new GT_Recipe(true, new ItemStack[]{GT_Utility.copyAmount(8, aInputs[0])}, new ItemStack[]{tOutput}, null, null, new FluidStack[]{new FluidStack(aFluids[0].getFluid(), (int) L)}, null, 256, 2, 0), false, false, true);

                tOutput = GT_ModHandler.getAllRecipeOutput(aTileEntity == null ? null : aTileEntity.getWorld(), aInputs[0], ItemList.DYE_ONLY_ITEMS[aDye.mIndex].get(1));
                if (tOutput != null)
                    return addRecipe(new GT_Recipe(true, new ItemStack[]{GT_Utility.copyAmount(1, aInputs[0])}, new ItemStack[]{tOutput}, null, null, new FluidStack[]{new FluidStack(aFluids[0].getFluid(), (int) L)}, null, 32, 2, 0), false, false, true);
            } else {
                if (aInputs[0].getItem() == Items.paper) {
                    if (!ItemList.Tool_DataStick.isStackEqual(aSpecialSlot, false, true)) return null;
                    NBTTagCompound tNBT = aSpecialSlot.getTagCompound();
                    if (tNBT == null || GT_Utility.isStringInvalid(tNBT.getString("title")) || GT_Utility.isStringInvalid(tNBT.getString("author")))
                        return null;

                    rRecipe = rRecipe.copy();
                    rRecipe.mCanBeBuffered = false;
                    rRecipe.mOutputs[0].setTagCompound(tNBT);
                    return rRecipe;
                }
                if (aInputs[0].getItem() == Items.map) {
                    if (!ItemList.Tool_DataStick.isStackEqual(aSpecialSlot, false, true)) return null;
                    NBTTagCompound tNBT = aSpecialSlot.getTagCompound();
                    if (tNBT == null || !tNBT.hasKey("map_id")) return null;

                    rRecipe = rRecipe.copy();
                    rRecipe.mCanBeBuffered = false;
                    rRecipe.mOutputs[0].setItemDamage(tNBT.getShort("map_id"));
                    return rRecipe;
                }
                if (ItemList.Paper_Punch_Card_Empty.isStackEqual(aInputs[0], false, true)) {
                    if (!ItemList.Tool_DataStick.isStackEqual(aSpecialSlot, false, true)) return null;
                    NBTTagCompound tNBT = aSpecialSlot.getTagCompound();
                    if (tNBT == null || !tNBT.hasKey("GT.PunchCardData")) return null;

                    rRecipe = rRecipe.copy();
                    rRecipe.mCanBeBuffered = false;
                    rRecipe.mOutputs[0].setTagCompound(GT_Utility.getNBTContainingString(new NBTTagCompound(), "GT.PunchCardData", tNBT.getString("GT.PunchCardData")));
                    return rRecipe;
                }
            }
            return rRecipe;
        }

        @Override
        public boolean containsInput(ItemStack aStack) {
            return true;
        }

        @Override
        public boolean containsInput(FluidStack aFluid) {
            return super.containsInput(aFluid) || Dyes.isAnyFluidDye(aFluid);
        }

        @Override
        public boolean containsInput(Fluid aFluid) {
            return super.containsInput(aFluid) || Dyes.isAnyFluidDye(aFluid);
        }
    }

    public static class GT_Recipe_Map_LargeBoilerFakeFuels extends GT_Recipe_Map {
    	
        public GT_Recipe_Map_LargeBoilerFakeFuels() {
            super(new NonBlockingHashSet<>(), "gt.recipe.largeboilerfakefuels", "Large Boiler", null, RES_PATH_GUI + "basicmachines/Default", 1, 0, 1, 0, 1, E, 1, E, true, true);
            GT_Recipe explanatoryRecipe = new GT_Recipe(true, new ItemStack[]{}, new ItemStack[]{}, null, null, null, null, 1, 1, 1);
            explanatoryRecipe.setNeiDesc("Not all solid fuels are listed.", "Any item that burns in a", "vanilla furnace will burn in", "a Large Boiler.");
            addRecipe(explanatoryRecipe);
        }
    	
    	public GT_Recipe addDenseLiquidRecipe(GT_Recipe recipe) {
    		return addRecipe(recipe, ((double)recipe.mSpecialValue) / 10);
    	}

    	public GT_Recipe addDieselRecipe(GT_Recipe recipe) {
    		return addRecipe(recipe,((double)recipe.mSpecialValue) / 40);
    	}

    	public void addSolidRecipes(ItemStack ... itemStacks) {
    		for (ItemStack itemStack : itemStacks){
    			addSolidRecipe(itemStack);
    		}
    	}
    	    	
    	public GT_Recipe addSolidRecipe(ItemStack fuelItemStack){
    		return addRecipe(new GT_Recipe(true, new ItemStack[]{fuelItemStack}, new ItemStack[]{}, null, null, null, null, 1, 0, GT_ModHandler.getFuelValue(fuelItemStack) / 1600), ((double)GT_ModHandler.getFuelValue(fuelItemStack)) / 1600);
    	}
    	
    	private GT_Recipe addRecipe(GT_Recipe recipe, double baseBurnTime){
			recipe = new GT_Recipe(recipe);
			//Some recipes will have a burn time like 15.9999999 and % always rounds down
			double floatErrorCorrection = 0.0001;
			
    		double bronzeBurnTime = baseBurnTime * 2 + floatErrorCorrection;
    		bronzeBurnTime -= bronzeBurnTime % 0.05;
    		double steelBurnTime = baseBurnTime * 1.5 + floatErrorCorrection;
    		steelBurnTime -= steelBurnTime % 0.05;
    		double titaniumBurnTime = baseBurnTime * 1.3 + floatErrorCorrection;
    		titaniumBurnTime -= titaniumBurnTime % 0.05;
    		double tungstensteelBurnTime = baseBurnTime * 1.2 + floatErrorCorrection;
    		tungstensteelBurnTime -= tungstensteelBurnTime % 0.05;
    		
    		recipe.setNeiDesc("Burn time in seconds:", 
    				String.format("Bronze Boiler: %.4f", bronzeBurnTime), 
    				String.format("Steel Boiler: %.4f", steelBurnTime), 
    				String.format("Titanium Boiler: %.4f", titaniumBurnTime), 
    				String.format("Tungstensteel Boiler: %.4f", tungstensteelBurnTime));
    		return super.addRecipe(recipe);
    	}
    	
    }
    
    public static class GT_Recipe_Map_LargeChemicalReactor extends GT_Recipe_Map{
        private static int TOTAL_INPUT_COUNT = 6;
    	private static int OUTPUT_COUNT = 2;
    	private static int FLUID_OUTPUT_COUNT = 4;
    	
        public GT_Recipe_Map_LargeChemicalReactor() {
            super(new NonBlockingHashSet<>(), "gt.recipe.largechemicalreactor", "Large Chemical Reactor", null, RES_PATH_GUI + "basicmachines/Default", 2, OUTPUT_COUNT, 0, 0, 1, E, 1, E, true, true);
        }

        @Override
        public GT_Recipe addRecipe(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, int[] aOutputChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
        	aOptimize = false;
        	ArrayList<ItemStack> adjustedInputs = new ArrayList<ItemStack>();
        	ArrayList<ItemStack> adjustedOutputs = new ArrayList<ItemStack>();
        	ArrayList<FluidStack> adjustedFluidInputs = new ArrayList<FluidStack>();
        	ArrayList<FluidStack> adjustedFluidOutputs = new ArrayList<FluidStack>();

            if (aInputs == null) {
                aInputs = new ItemStack[0];
            }
        	for (ItemStack input : aInputs) {
        		FluidStack inputFluidContent = FluidContainerRegistry.getFluidForFilledItem(input);
        		if (inputFluidContent != null) {
        			inputFluidContent.amount *= input.stackSize;
                    if (inputFluidContent.getFluid().getName().equals("ic2steam")) {
                        inputFluidContent = GT_ModHandler.getSteam(inputFluidContent.amount);
                    }
        			adjustedFluidInputs.add(inputFluidContent);
        		} else {
        			ItemData itemData = GT_OreDictUnificator.getItemData(input);
        			if (itemData != null && itemData.hasValidPrefixMaterialData() && itemData.mMaterial.mMaterial == Materials.Empty) {
        				continue;
        			} else {
        				if (itemData != null && itemData.hasValidPrefixMaterialData() && itemData.mPrefix == OrePrefixes.cell) {
        					ItemStack dustStack = itemData.mMaterial.mMaterial.getDust(input.stackSize);
        					if (dustStack != null) {
        						adjustedInputs.add(dustStack);
        					} else {
        						adjustedInputs.add(input);
        					}
        				} else {
        					adjustedInputs.add(input);
        				}
        			}
        		}

                if (aFluidInputs == null) {
                    aFluidInputs = new FluidStack[0];
                }
        	}
        	for (FluidStack fluidInput : aFluidInputs) {
        		adjustedFluidInputs.add(fluidInput);
        	}
        	aInputs = adjustedInputs.toArray(new ItemStack[adjustedInputs.size()]);
        	aFluidInputs = adjustedFluidInputs.toArray(new FluidStack[adjustedFluidInputs.size()]);
            if (aOutputs == null) {
                aOutputs = new ItemStack[0];
            }
        	
        	for (ItemStack output : aOutputs) {
        		FluidStack outputFluidContent = FluidContainerRegistry.getFluidForFilledItem(output);
        		if (outputFluidContent != null) {
        			outputFluidContent.amount *= output.stackSize;
                    if (outputFluidContent.getFluid().getName().equals("ic2steam")) {
                        outputFluidContent = GT_ModHandler.getSteam(outputFluidContent.amount);
                    }
        			adjustedFluidOutputs.add(outputFluidContent);
        		} else {
        			ItemData itemData = GT_OreDictUnificator.getItemData(output);
        			if (itemData != null && itemData.hasValidPrefixMaterialData() && itemData.mMaterial.mMaterial == Materials.Empty) {
        				continue;
        			} else {
        				adjustedOutputs.add(output);
        			}
        		}
        	}
            if (aFluidOutputs == null) {
                aFluidOutputs = new FluidStack[0];
            }
        	for (FluidStack fluidOutput : aFluidOutputs) {
        		adjustedFluidOutputs.add(fluidOutput);
        	}
        	aOutputs = adjustedOutputs.toArray(new ItemStack[adjustedOutputs.size()]);
        	aFluidOutputs = adjustedFluidOutputs.toArray(new FluidStack[adjustedFluidOutputs.size()]);
        	
            return addRecipe(new GT_Recipe_LargeChemicalReactor(aOptimize, aInputs, aOutputs, aSpecial, aOutputChances, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue));
        }

        private static class GT_Recipe_LargeChemicalReactor extends GT_Recipe{

            protected GT_Recipe_LargeChemicalReactor(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecialItems, int[] aChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            	super(aOptimize, aInputs, aOutputs, aSpecialItems, aChances, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue);
            }

			@Override
			public ArrayList<PositionedStack> getInputPositionedStacks() {
			    int itemLimit = Math.min(mInputs.length, TOTAL_INPUT_COUNT);
			    int fluidLimit = Math.min(mFluidInputs.length, TOTAL_INPUT_COUNT - itemLimit);
			    int inputlimit = itemLimit + fluidLimit;
			    int j = 0;

				ArrayList<PositionedStack> inputStacks = new ArrayList<PositionedStack>(inputlimit);
				
				for (int i = 0; i < itemLimit; i++, j++) {
                    if (GT_Values.allow_broken_recipemap) {
                        if (this != null && this.mInputs != null && this.mInputs[i] != null)
                            inputStacks.add(new FixedPositionedStack(this.mInputs[i].copy(), 48 - j % 3 * 18, (j >= 3 ? 5 : 23)));
                        else {
                            if (this.mOutputs != null && this.mOutputs.length > 0 && this.mOutputs[0] != null)
                                GT_Log.out.println("recipe " + this.toString() + " Output 0:" + this.mOutputs[0].getDisplayName() + " has errored!");
                            else
                                GT_Log.out.println("recipe " + this.toString() + " has errored!");
                            inputStacks.add(new FixedPositionedStack(new ItemStack(Items.command_block_minecart), 48 - j % 3 * 18, (j >= 3 ? 5 : 23)));
                        }
                    }else
                        inputStacks.add(new FixedPositionedStack(this.mInputs[i].copy(), 48 - j % 3 * 18, (j >= 3 ? 5 : 23)));
				}
				
				for (int i = 0; i < fluidLimit; i++, j++) {
                    if (GT_Values.allow_broken_recipemap) {
                        if (this != null && this.mFluidInputs != null && this.mFluidInputs[i] != null)
                            inputStacks.add(new FixedPositionedStack(GT_Utility.getFluidDisplayStack(this.mFluidInputs[i], true), 48 - j % 3 * 18, (j >= 3 ? 5 : 23)));
                        else {
                            if (this.mOutputs != null && this.mOutputs.length > 0 && this.mOutputs[0] != null)
                                GT_Log.out.println("recipe " + this.toString() + " Output 0:" + this.mOutputs[0].getDisplayName() + " has errored!");
                            else
                                GT_Log.out.println("recipe " + this.toString() + " has errored!");
                        }
                    }else
                        inputStacks.add(new FixedPositionedStack(GT_Utility.getFluidDisplayStack(this.mFluidInputs[i], true), 48 - j % 3 * 18, (j >= 3 ? 5 : 23)));
                }
				
				return inputStacks;
			}

			@Override
			public ArrayList<PositionedStack> getOutputPositionedStacks() {
				int itemLimit = Math.min(mOutputs.length, OUTPUT_COUNT);
				int fluidLimit = Math.min(mFluidOutputs.length, FLUID_OUTPUT_COUNT);
				ArrayList<PositionedStack> outputStacks = new ArrayList<PositionedStack>(itemLimit + fluidLimit);
				
				for (int i = 0; i < itemLimit; i++) {
                    outputStacks.add(new FixedPositionedStack(this.mOutputs[i].copy(), 102 + i * 18, 5));
				}
				
				for (int i = 0; i < fluidLimit; i++) {
					outputStacks.add(new FixedPositionedStack(GT_Utility.getFluidDisplayStack(this.mFluidOutputs[i], true), 102 + i * 18, 23));
				}
				
				return outputStacks;
			}

            
        }
    }
    public static class GT_Recipe_Map_DistillationTower extends GT_Recipe_Map {
   	private static final int FLUID_OUTPUT_COUNT = 11;
   	private static final int ROW_SIZE = 3;

   	public GT_Recipe_Map_DistillationTower() {
        super(new NonBlockingHashSet<>(), "gt.recipe.distillationtower", "Distillation Tower", null, RES_PATH_GUI + "basicmachines/DistillationTower", 2, 4, 0, 0, 1, E, 1, E, true, true);
    }

    @Override
    public GT_Recipe addRecipe(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, int[] aOutputChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
        return addRecipe(new GT_Recipe_DistillationTower(aOptimize, aInputs, aOutputs, aSpecial, aOutputChances, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue));
    }

        @Override
        public GT_Recipe addRecipe(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecial, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
            return addRecipe(aOptimize, aInputs, aOutputs, aSpecial, null, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue);
              }

              private static class GT_Recipe_DistillationTower extends GT_Recipe{
                   protected GT_Recipe_DistillationTower(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecialItems, int[] aChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue) {
                       super(aOptimize, aInputs, aOutputs, aSpecialItems, aChances, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue);
                   }

                   @Override
                   public ArrayList<PositionedStack> getInputPositionedStacks() {
                       ArrayList<PositionedStack> inputStacks = new ArrayList<PositionedStack>(1);

                       if (this.mFluidInputs.length > 0 && this.mFluidInputs[0] != null) {
                           inputStacks.add(new FixedPositionedStack(GT_Utility.getFluidDisplayStack(this.mFluidInputs[0], true), 48, 52));
                       }
                       return inputStacks;
                   }
                  @Override
                  public ArrayList<PositionedStack> getOutputPositionedStacks() {
                      int fluidLimit = Math.min(mFluidOutputs.length, FLUID_OUTPUT_COUNT);
                      ArrayList<PositionedStack> outputStacks = new ArrayList<PositionedStack>(1 + fluidLimit);

                      if (this.mOutputs.length > 0 && this.mOutputs[0] != null) {
                          outputStacks.add(new FixedPositionedStack(this.getOutput(0), 102, 52));
                      }

                      for (int i = 0; i < fluidLimit; i++) {
                          int x = 102 + ((i + 1) % ROW_SIZE) * 18;
                          int y =  52 - ((i + 1) / ROW_SIZE) * 18;
                          outputStacks.add(new FixedPositionedStack(GT_Utility.getFluidDisplayStack(this.mFluidOutputs[i], true), x, y));
                      }
                      return outputStacks;
                  }

           	}
    }

    public static class GT_Recipe_WithAlt extends GT_Recipe {

    	ItemStack[][] mOreDictAlt;

		public GT_Recipe_WithAlt(boolean aOptimize, ItemStack[] aInputs, ItemStack[] aOutputs, Object aSpecialItems, int[] aChances, FluidStack[] aFluidInputs, FluidStack[] aFluidOutputs, int aDuration, int aEUt, int aSpecialValue, ItemStack[][] aAlt) {
			super(aOptimize, aInputs, aOutputs, aSpecialItems, aChances, aFluidInputs, aFluidOutputs, aDuration, aEUt, aSpecialValue);
			mOreDictAlt = aAlt;
		}

		
		public Object getAltRepresentativeInput(int aIndex) {
	        if (aIndex < 0) return null;
	        if (aIndex < mOreDictAlt.length) {
	        	if (mOreDictAlt[aIndex] != null && mOreDictAlt[aIndex].length > 0) {
	        		ItemStack[] rStacks = new ItemStack[mOreDictAlt[aIndex].length];
	        		for (int i = 0; i < mOreDictAlt[aIndex].length; i++) {
	        			rStacks[i] = GT_Utility.copy(mOreDictAlt[aIndex][i]);
	        		}
	        		return rStacks;
	        	}
	        }
	        if (aIndex >= mInputs.length) return null;
	        return GT_Utility.copy(mInputs[aIndex]);
	    }
    	
    }
}
