package com.gmail.nossr50.skills.acrobatics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SkillType;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.skills.CombatUtils;
import com.gmail.nossr50.util.skills.ParticleEffectUtils;
import com.gmail.nossr50.util.skills.SkillUtils;

public class AcrobaticsManager extends SkillManager {
    private int fallTries = 0;
    Location lastFallLocation;

    public AcrobaticsManager(McMMOPlayer mcMMOPlayer) {
        super(mcMMOPlayer, SkillType.ACROBATICS);
    }

    /**
     * Check if the player is allowed to use Roll,
     * checks permissions and exploit prevention.
     *
     * @return true if the player is allowed to use Roll
     */
    public boolean canRoll() {
        Player player = getPlayer();

        return (player.getItemInHand().getType() != Material.ENDER_PEARL) && !exploitPrevention() && Permissions.roll(player);
    }

    /**
     * Check if the player is allowed to use Dodge
     *
     * @param damager {@link Entity} that caused damage
     * @return true if the player can Dodge damage from damager
     */
    public boolean canDodge(Entity damager) {
        if (Permissions.dodge(getPlayer())) {
            if (damager instanceof LightningStrike && Acrobatics.dodgeLightningDisabled) {
                return false;
            }

            return CombatUtils.shouldProcessSkill(damager, skill);
        }

        return false;
    }

    /**
     * Handle the damage reduction and XP gain from the Dodge ability
     *
     * @param damage The amount of damage initially dealt by the event
     * @return the modified event damage if the ability was successful, the original event damage otherwise
     */
    public double dodgeCheck(double damage) {
        double modifiedDamage = Acrobatics.calculateModifiedDodgeDamage(damage, Acrobatics.dodgeDamageModifier);
        Player player = getPlayer();

        if (!isFatal(modifiedDamage) && SkillUtils.activationSuccessful(getSkillLevel(), getActivationChance(), Acrobatics.dodgeMaxChance, Acrobatics.dodgeMaxBonusLevel)) {
            ParticleEffectUtils.playDodgeEffect(player);

            if (mcMMOPlayer.useChatNotifications()) {
                player.sendMessage(LocaleLoader.getString("Acrobatics.Combat.Proc"));
            }

            // Why do we check respawn cooldown here?
            if (SkillUtils.cooldownExpired(mcMMOPlayer.getRespawnATS(), Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS)) {
                applyXpGain((float) (damage * Acrobatics.dodgeXpModifier));
            }

            return modifiedDamage;
        }

        return damage;
    }

    /**
     * Handle the damage reduction and XP gain from the Roll ability
     *
     * @param damage The amount of damage initially dealt by the event
     * @return the modified event damage if the ability was successful, the original event damage otherwise
     */
    public double rollCheck(double damage) {
        Player player = getPlayer();

        if (player.isSneaking() && Permissions.gracefulRoll(player)) {
            return gracefulRollCheck(damage);
        }

        double modifiedDamage = Acrobatics.calculateModifiedRollDamage(damage, Acrobatics.rollThreshold);

        if (!isFatal(modifiedDamage) && isSuccessfulRoll(Acrobatics.rollMaxChance, Acrobatics.rollMaxBonusLevel)) {
            player.sendMessage(LocaleLoader.getString("Acrobatics.Roll.Text"));
            applyXpGain(calculateRollXP(damage, true));

            return modifiedDamage;
        }
        else if (!isFatal(damage)) {
            applyXpGain(calculateRollXP(damage, false));
        }

        lastFallLocation = player.getLocation();

        return damage;
    }

    /**
     * Handle the damage reduction and XP gain from the Graceful Roll ability
     *
     * @param damage The amount of damage initially dealt by the event
     * @return the modified event damage if the ability was successful, the original event damage otherwise
     */
    private double gracefulRollCheck(double damage) {
        double modifiedDamage = Acrobatics.calculateModifiedRollDamage(damage, Acrobatics.gracefulRollThreshold);

        if (!isFatal(modifiedDamage) && isSuccessfulRoll(Acrobatics.gracefulRollMaxChance, Acrobatics.gracefulRollMaxBonusLevel)) {
            getPlayer().sendMessage(LocaleLoader.getString("Acrobatics.Ability.Proc"));
            applyXpGain(calculateRollXP(damage, true));

            return modifiedDamage;
        }
        else if (!isFatal(damage)) {
            applyXpGain(calculateRollXP(damage, false));
        }

        return damage;
    }

    /**
     * Check if exploit prevention should kick in.
     *
     * @return true when exploits are detected, false if otherwise
     */
    public boolean exploitPrevention() {
        if (!Config.getInstance().getAcrobaticsAFKDisabled()) {
            return false;
        }

        if (getPlayer().isInsideVehicle()) {
            return true;
        }

        Location fallLocation = getPlayer().getLocation();

        boolean sameLocation = (lastFallLocation != null && Misc.isNear(lastFallLocation, fallLocation, 2));

        fallTries = sameLocation ? fallTries + 1 : Math.max(fallTries - 1, 0);
        lastFallLocation = fallLocation;

        return fallTries > Config.getInstance().getAcrobaticsAFKMaxTries();
    }

    /**
     * Check if Roll was successful
     *
     * @param maxChance Maximum chance of a successful Roll
     * @param maxLevel Maximum bonus level of Roll
     * @return true if it was a successful Roll, false otherwise
     */
    private boolean isSuccessfulRoll(double maxChance, int maxLevel) {
        return (maxChance / maxLevel) * Math.min(getSkillLevel(), maxLevel) > Misc.getRandom().nextInt(activationChance);
    }

    /**
     * Check if a fall is fatal
     *
     * @param damage amount of damage taken from the fall
     * @return true if the fall is fatal, false otherwise
     */
    private boolean isFatal(double damage) {
        return getPlayer().getHealth() - damage < 1;
    }

    /**
     * Calculate the amount of XP gained from falling
     *
     * @param damage amount of damage taken in the fall
     * @param isRoll boolean if the player was rolling
     * @return amount of XP gained
     */
    private float calculateRollXP(double damage, boolean isRoll) {
        ItemStack boots = getPlayer().getInventory().getBoots();
        float xp = (float) (damage * (isRoll ? Acrobatics.rollXpModifier : Acrobatics.fallXpModifier));

        if (boots != null && boots.containsEnchantment(Enchantment.PROTECTION_FALL)) {
            xp *= Acrobatics.featherFallXPModifier;
        }

        return xp;
    }
}
