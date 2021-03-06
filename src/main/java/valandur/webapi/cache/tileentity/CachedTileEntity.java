package valandur.webapi.cache.tileentity;

import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.block.tileentity.carrier.TileEntityCarrier;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import valandur.webapi.api.cache.tileentity.ICachedTileEntity;
import valandur.webapi.api.serialize.JsonDetails;
import valandur.webapi.api.cache.CachedObject;
import valandur.webapi.cache.misc.CachedInventory;
import valandur.webapi.api.cache.world.CachedLocation;

import java.util.Optional;

public class CachedTileEntity extends CachedObject<TileEntity> implements ICachedTileEntity {

    private String type;
    @Override
    public String getType() {
        return type;
    }

    private CachedLocation location;
    public CachedLocation getLocation() {
        return location;
    }

    protected CachedInventory inventory;
    @JsonDetails
    public CachedInventory getInventory() {
        return inventory;
    }


    public CachedTileEntity(TileEntity te) {
        super(te);

        this.type = te.getType() != null ? te.getType().getId() : null;
        this.location = new CachedLocation(te.getLocation());

        if (te instanceof TileEntityCarrier) {
            this.inventory = new CachedInventory(((TileEntityCarrier)te).getInventory());
        }
    }

    @Override
    public Optional<TileEntity> getLive() {
        Optional<Location> obj = location.getLive();
        return obj.flatMap(o -> ((Location<World>) o).getTileEntity());
    }

    @Override
    public String getLink() {
        return "/api/tile-entity/" + location.getWorld().getUUID() + "/" + location.getPosition().getFloorX() + "/" +
                location.getPosition().getFloorY() + "/" + location.getPosition().getFloorZ();
    }
}
