package org.evd.game.StageService.scene.battle;

import org.evd.game.StageService.scene.logic.BuffLogic;
import org.evd.game.StageService.scene.logic.SkillLogic;

import java.util.HashMap;
import java.util.Map;

/** 场景中可受伤、施法和承载 Buff 的战斗单位。 */
public abstract class BattleUnit {
    private final long id;
    private final String name;
    private final int level;
    private final int maxHp;
    private final int baseAttack;
    private final int baseDefense;
    private int hp;
    private int mp;
    private final Map<Integer, BattleBuff> buffs = new HashMap<>();
    private final Map<Integer, Long> skillCooldowns = new HashMap<>();
    private final BuffLogic buffLogic;
    private final SkillLogic skillLogic;

    protected BattleUnit(long id, String name, int level, int hp, int attack, int defense, int mp,
                         BuffLogic buffLogic, SkillLogic skillLogic) {
        if (id <= 0L || hp <= 0) {
            throw new IllegalArgumentException("战斗单位属性非法: id=" + id + ", hp=" + hp);
        }
        this.id = id;
        this.name = name;
        this.level = level;
        this.maxHp = hp;
        this.hp = hp;
        this.baseAttack = Math.max(0, attack);
        this.baseDefense = Math.max(0, defense);
        this.mp = Math.max(0, mp);
        this.buffLogic = buffLogic;
        this.skillLogic = skillLogic;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getMp() {
        return mp;
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public int getAttack() {
        return Math.max(0, baseAttack + buffLogic.getAttackDelta(this));
    }

    public int getDefense() {
        return Math.max(0, baseDefense + buffLogic.getDefenseDelta(this));
    }

    public void takeDamage(int damage) {
        if (damage <= 0 || !isAlive()) {
            return;
        }
        hp = Math.max(0, hp - damage);
    }

    public boolean addBuff(int buffId, long now) {
        return buffLogic.addBuff(this, buffId, now);
    }

    public void update(long now) {
        buffLogic.update(this, now);
    }

    public boolean useSkill(int skillId, int level, BattleUnit target, long now) {
        return skillLogic.useSkill(this, target, skillId, level, now);
    }

    public boolean consumeMp(int amount) {
        if (amount < 0 || mp < amount) {
            return false;
        }
        mp -= amount;
        return true;
    }

    public Map<Integer, BattleBuff> buffs() {
        return buffs;
    }

    public Map<Integer, Long> skillCooldowns() {
        return skillCooldowns;
    }
}
