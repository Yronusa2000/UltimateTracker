package fr.yronusa.sauron;

import fr.yronusa.sauron.Config.Config;
import fr.yronusa.sauron.Config.TrackingRule;
import fr.yronusa.sauron.Database.Database;
import fr.yronusa.sauron.Event.BlacklistedItemDetectedEvent;
import fr.yronusa.sauron.Event.DupeDetectedEvent;
import fr.yronusa.sauron.Event.ItemStartTrackingEvent;
import fr.yronusa.sauron.Event.StackedItemDetectedEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class TrackedItem {


    public ItemMutable item;
    public UUID originalID;
    public Timestamp lastUpdate; // Last update of the item at format YYYY.MM.DD.hh.mm

    // Used to get the associated TrackedItem from an ItemMutable directly.
    // The ItemMutable is necessary to be able to update data on the item more easily.
    public TrackedItem(ItemMutable item) {
        this.item = item;
        this.originalID = item.getID();
        this.lastUpdate = item.getLastUpdate();

    }

    public static boolean shouldBeTrack(ItemMutable i) {
        ItemStack item = i.getItem();
        if(item == null) return false;
        if(!Config.trackStackedItems && item.getAmount() != 1) return false;

        for(TrackingRule rule : Config.trackingRules){
            if(rule.test(i.getItem())) return true;
        }

        return false;
    }

    public UUID getOriginalID() {
        return originalID;
    }
    public ItemStack getItem() {
        return this.item.item;
    }

    public List<InventoryLocation> getLastInventories(){
        return null;
    }

    public ItemMutable getItemMutable() {
        return this.item;
    }

    public static TrackedItem startTracking(ItemMutable item){

        if(item.hasTrackingID()){
            TrackedItem trackedItem = new TrackedItem(item);
            trackedItem.update();
            return trackedItem;
        }

        else{
            UUID originalID = UUID.randomUUID();
            item.setTrackable(originalID, Sauron.getActualDate());
            TrackedItem trackedItem = new TrackedItem(item);

            if(Sauron.database) {
                Database.add(trackedItem);
            }
            ItemStartTrackingEvent trackEvent = new ItemStartTrackingEvent(trackedItem);
            Bukkit.getPluginManager().callEvent(trackEvent);
            return trackedItem;
        }
    }

    public static void update(Inventory inv){
        int position = 0;
        for(ItemStack i : inv.getContents()) {

            ItemMutable item = new ItemMutable(i, inv, position);
            if(item.hasTrackingID()){
                (new TrackedItem(item)).update();
                return;
            }
            if(shouldBeTrack(item)){
                startTracking(item);
            }
        }
    }

    public Player getPlayer(){
        if(this.getItemMutable().getInventory().getHolder() instanceof Player p){
            return p;
        }

        return null;
    }


    public String getBase64(){
        String base = itemStackArrayToBase64(new ItemStack[]{this.getItem()});
        return base.replaceAll("\\n", "");
    }

    public Timestamp getLastUpdateItem(){
        return this.getItemMutable().getLastUpdate();
    }

    public void update(boolean forceUpdate) {

        if(!forceUpdate && !shouldUpdate()){
            return;
        }

        if(this.getItem() != null && Config.clearStackedItems && this.getItem().getAmount() > 1){
            StackedItemDetectedEvent stackedItemDetected = new StackedItemDetectedEvent(this);
            Bukkit.getPluginManager().callEvent(stackedItemDetected);
            return;
        }

        CompletableFuture<Boolean> isBlacklisted = CompletableFuture.supplyAsync(() -> Database.isBlacklisted(this)).exceptionally(error -> false);
        CompletableFuture<Boolean> isDupli = CompletableFuture.supplyAsync(() -> Database.isDuplicated(this)).exceptionally(error -> {
            Timestamp newDate = Sauron.getActualDate();
            if(Sauron.database) Database.update(this, newDate);
            this.getItemMutable().updateDate(newDate);
            return false;
        });
        CompletableFuture<Pair<Boolean,Boolean>> combinedResult = isBlacklisted.thenCombine(isDupli, (blacklist, dupe) -> new Pair<>(){

            @Override
            public Boolean setValue(Boolean value) {
                return null;
            }

            @Override
            public Boolean getLeft() {
                if(blacklist == null) return false;
                return blacklist;
            }

            @Override
            public Boolean getRight() {
                return dupe;
            }
        });

        combinedResult.thenAccept(resPair -> {
            System.out.println("is blacklisted : " + resPair.getLeft());
            System.out.println("is duplicated : " + resPair.getRight());
            if(resPair.getLeft()){
                System.out.println("prout1");
                // Blacklisted item detected:
                BlacklistedItemDetectedEvent blacklistDetectEvent = new BlacklistedItemDetectedEvent(this);
                // Necessary because in the newest version of Spigot, Event can't be called from async thread.
                Bukkit.getScheduler().runTask(Sauron.getInstance(), () -> Bukkit.getPluginManager().callEvent(blacklistDetectEvent));
                return;
            }

            if(resPair.getRight()){
                System.out.println("prout2");
                // Dupe item detected:
                DupeDetectedEvent dupeDetectEvent = new DupeDetectedEvent(this, this.getPlayer());
                Bukkit.getScheduler().runTask(Sauron.getInstance(), () -> Bukkit.getPluginManager().callEvent(dupeDetectEvent));
                return;
            }

            // If the item is neither blacklisted nor duplicated, we update it.
            System.out.println("prout3");
            Timestamp newDate = Sauron.getActualDate();
            Database.update(this, newDate);
            this.getItemMutable().updateDate(newDate);

        });
       /** // After updating the item, checks if it is blacklisted.
        CompletableFuture<Boolean> isBlacklisted = CompletableFuture.supplyAsync(() -> Database.isBlacklisted(this));
        isBlacklisted.exceptionally(error -> false);
        isBlacklisted.thenAccept((res) -> {
            if(res){
                BlacklistedItemDetectedEvent blacklistDetectEvent = new BlacklistedItemDetectedEvent(this);
                // Necessary because in the newest version of Spigot, Event can't be called from async thread.
                Bukkit.getScheduler().runTask(Sauron.getInstance(), () -> Bukkit.getPluginManager().callEvent(blacklistDetectEvent));
            }
        });

        // Checks if the item is duplicated, and if so, fires the appropriate event.
        CompletableFuture<Boolean> isDupli = CompletableFuture.supplyAsync(() -> Database.isDuplicated(this));
        isDupli.exceptionally(error -> {
            if(Sauron.database) Database.update(this, newDate);
            this.getItemMutable().updateDate(newDate);
            return false;
        });
        isDupli.thenAccept((res) -> {
            if(res){
                DupeDetectedEvent dupeDetectEvent = new DupeDetectedEvent(this, this.getPlayer());

                // Necessary because in the newest version of Spigot, Event can't be called from async thread.
                Bukkit.getScheduler().runTask(Sauron.getInstance(), () -> Bukkit.getPluginManager().callEvent(dupeDetectEvent));
            }

            else{
                Database.update(this, newDate);
                this.getItemMutable().updateDate(newDate);
            }
        });**/

    }

    public void update() {
        update(false);
    }

    private boolean shouldUpdate() {
        Timestamp itemTimestamp = this.getLastUpdateItem();
        Timestamp actualTime = Sauron.getActualDate();
        long difference = actualTime.getTime() - itemTimestamp.getTime();
        return difference / 1000 >= Config.delay;
    }


    public static String itemStackArrayToBase64(ItemStack[] items) throws IllegalStateException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (int i = 0; i < items.length; i++) {
                dataOutput.writeObject(items[i]);
            }
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    public void quarantine(){
        this.getItemMutable().delete();
    }

    public void resetUUID() {
        UUID newID = UUID.randomUUID();
        this.originalID = newID;
        this.getItemMutable().changeUUID(newID);
    }

}
