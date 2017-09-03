package valandur.webapi.cache.misc;

import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.Slot;
import valandur.webapi.api.cache.misc.ICachedCatalogType;
import valandur.webapi.api.cache.misc.ICachedInventory;
import valandur.webapi.cache.CachedObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CachedInventory extends CachedObject implements ICachedInventory {

    private String name;
    @Override
    public String getName() {
        return name;
    }

    private ICachedCatalogType type;
    @Override
    public ICachedCatalogType getType() {
        return type;
    }

    private int capacity;
    @Override
    public int getCapacity() {
        return capacity;
    }

    private int totalItems;
    @Override
    public int getTotalItems() {
        return totalItems;
    }

    private List<ItemStack> items;
    @Override
    public List<ItemStack> getItems() {
        return items;
    }


    public CachedInventory(Inventory inv) {
        super(inv);

        this.name = inv.getName().get();
        this.capacity = inv.capacity();
        this.totalItems = inv.totalItems();
        this.type = new CachedCatalogType(inv.getArchetype());

        items = new ArrayList<>();
        try {
            for (Inventory subInv : inv.slots()) {
                Slot slot = (Slot) subInv;
                Optional<ItemStack> optItem = slot.peek();
                optItem.ifPresent(itemStack -> items.add(itemStack.copy()));
            }
        } catch (AbstractMethodError ignored) {}
    }
}
