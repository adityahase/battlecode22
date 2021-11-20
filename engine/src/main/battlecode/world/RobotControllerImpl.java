package battlecode.world;

import battlecode.common.*;
import static battlecode.common.GameActionExceptionType.*;
import battlecode.instrumenter.RobotDeathException;
import battlecode.schema.Action;

import java.util.*;


/**
 * The actual implementation of RobotController. Its methods *must* be called
 * from a player thread.
 *
 * It is theoretically possible to have multiple for a single InternalRobot, but
 * that may cause problems in practice, and anyway why would you want to?
 *
 * All overriden methods should assertNotNull() all of their (Object) arguments,
 * if those objects are not explicitly stated to be nullable.
 */
public final strictfp class RobotControllerImpl implements RobotController {

    /**
     * The world the robot controlled by this controller inhabits.
     */
    private final GameWorld gameWorld;

    /**
     * The robot this controller controls.
     */
    private final InternalRobot robot;

    /**
     * An rng based on the world seed.
     */
    private static Random random;

    /**
     * Create a new RobotControllerImpl
     *
     * @param gameWorld the relevant world
     * @param robot the relevant robot
     */
    public RobotControllerImpl(GameWorld gameWorld, InternalRobot robot) {
        this.gameWorld = gameWorld;
        this.robot = robot;

        this.random = new Random(gameWorld.getMapSeed());
    }

    // *********************************
    // ******** INTERNAL METHODS *******
    // *********************************

    /**
     * Throw a null pointer exception if an object is null.
     *
     * @param o the object to test
     */
    private static void assertNotNull(Object o) {
        if (o == null) {
            throw new NullPointerException("Argument has an invalid null value");
        }
    }

    @Override
    public int hashCode() {
        return robot.getID();
    }

    // *********************************
    // ****** GLOBAL QUERY METHODS *****
    // *********************************

    @Override
    public int getRoundNum() {
        return gameWorld.getCurrentRound();
    }

    @Override
    public int getTeamVotes() {
        return gameWorld.getTeamInfo().getVotes(getTeam());
    }

    @Override
    public int getRobotCount() {
        return gameWorld.getObjectInfo().getRobotCount(getTeam());
    }

    @Override
    public double getEmpowerFactor(Team team, int roundsInFuture) {
        return gameWorld.getTeamInfo().getBuff(team, getRoundNum() + roundsInFuture);
    }

    // *********************************
    // ****** UNIT QUERY METHODS *******
    // *********************************

    @Override
    public int getID() {
        return this.robot.getID();
    }

    @Override
    public Team getTeam() {
        return this.robot.getTeam();
    }

    @Override
    public RobotType getType() {
        return this.robot.getType();
    }

    @Override
    public MapLocation getLocation() {
        return this.robot.getLocation();
    }

    private InternalRobot getRobotByID(int id) {
        if (!gameWorld.getObjectInfo().existsRobot(id))
            return null;
        return this.gameWorld.getObjectInfo().getRobotByID(id);
    }
 
    public int getInfluence() {
        return this.robot.getInfluence();
    }

    public int getConviction() {
        return this.robot.getConviction();  
    }


    // ***********************************
    // ****** GENERAL SENSOR METHODS *****
    // ***********************************

    @Override
    public boolean onTheMap(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        if (!this.robot.canSenseLocation(loc))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Target location not within sensor range");
        return gameWorld.getGameMap().onTheMap(loc);
    }

    private void assertCanSenseLocation(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        if (!this.robot.canSenseLocation(loc))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Target location not within sensor range");
        if (!gameWorld.getGameMap().onTheMap(loc))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Target location is not on the map");
    }

    @Override
    public boolean canSenseLocation(MapLocation loc) {
        try {
            assertCanSenseLocation(loc);
            return true;
        } catch (GameActionException e) { return false; }
    }

    @Override
    public boolean canSenseRadiusSquared(int radiusSquared) {
        return this.robot.canSenseRadiusSquared(radiusSquared);
    }

    private void assertCanDetectLocation(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        if (!this.robot.canDetectLocation(loc))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Target location not within detection range");
        if (!gameWorld.getGameMap().onTheMap(loc))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Target location is not on the map");
    }

    @Override
    public boolean canDetectLocation(MapLocation loc) {
        try {
            assertCanDetectLocation(loc);
            return true;
        } catch (GameActionException e) { return false; }
    }

    @Override
    public boolean canDetectRadiusSquared(int radiusSquared) {
        return this.robot.canDetectRadiusSquared(radiusSquared);
    }

    @Override
    public boolean isLocationOccupied(MapLocation loc) throws GameActionException {
        assertCanDetectLocation(loc);
        return this.gameWorld.getRobot(loc) != null;
    }

    @Override
    public RobotInfo senseRobotAtLocation(MapLocation loc) throws GameActionException {
        assertCanSenseLocation(loc);
        InternalRobot bot = gameWorld.getRobot(loc);
        if (bot != null)
            return bot.getRobotInfo(getType().canTrueSense());
        return null;
    }

    @Override
    public boolean canSenseRobot(int id) {
        InternalRobot sensedRobot = getRobotByID(id);
        return sensedRobot == null ? false : canSenseLocation(sensedRobot.getLocation());
    }

    @Override
    public RobotInfo senseRobot(int id) throws GameActionException {
        if (!canSenseRobot(id))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Can't sense given robot; It may not exist anymore");
        return getRobotByID(id).getRobotInfo(getType().canTrueSense());
    }

    @Override
    public RobotInfo[] senseNearbyRobots() {
        return senseNearbyRobots(-1);
    }

    @Override
    public RobotInfo[] senseNearbyRobots(int radiusSquared) {
        return senseNearbyRobots(radiusSquared, null);
    }

    @Override
    public RobotInfo[] senseNearbyRobots(int radiusSquared, Team team) {
        return senseNearbyRobots(getLocation(), radiusSquared, team);
    }

    @Override
    public RobotInfo[] senseNearbyRobots(MapLocation center, int radiusSquared, Team team) {
        assertNotNull(center);
        int actualRadiusSquared = radiusSquared == -1 ? getType().sensorRadiusSquared : Math.min(radiusSquared, getType().sensorRadiusSquared);
        InternalRobot[] allSensedRobots = gameWorld.getAllRobotsWithinRadiusSquared(center, actualRadiusSquared);
        List<RobotInfo> validSensedRobots = new ArrayList<>();
        for (InternalRobot sensedRobot : allSensedRobots) {
            // check if this robot
            if (sensedRobot.equals(this.robot))
                continue;
            // check if can sense
            if (!canSenseLocation(sensedRobot.getLocation()))
                continue; 
            // check if right team
            if (team != null && sensedRobot.getTeam() != team)
                continue;
            validSensedRobots.add(sensedRobot.getRobotInfo(getType().canTrueSense()));
        }
        return validSensedRobots.toArray(new RobotInfo[validSensedRobots.size()]);
    }

    @Override
    public MapLocation[] detectNearbyRobots() {
        return detectNearbyRobots(-1);
    }

    @Override
    public MapLocation[] detectNearbyRobots(int radiusSquared) {
        return detectNearbyRobots(getLocation(), radiusSquared);
    }

    @Override
    public MapLocation[] detectNearbyRobots(MapLocation center, int radiusSquared) {
        assertNotNull(center);
        int actualRadiusSquared = radiusSquared == -1 ? getType().detectionRadiusSquared : Math.min(radiusSquared, getType().detectionRadiusSquared);
        InternalRobot[] allDetectedRobots = gameWorld.getAllRobotsWithinRadiusSquared(center, actualRadiusSquared);
        List<MapLocation> validDetectedRobots = new ArrayList<>();
        for (InternalRobot detectedRobot : allDetectedRobots) {
            // check if this robot
            if (detectedRobot.equals(this.robot))
                continue;
            // check if can detect
            if (!canDetectLocation(detectedRobot.getLocation()))
                continue;
            validDetectedRobots.add(detectedRobot.getLocation());
        }
        return validDetectedRobots.toArray(new MapLocation[validDetectedRobots.size()]);
    }

    @Override 
    public double sensePassability(MapLocation loc) throws GameActionException {
        assertCanSenseLocation(loc);
        return this.gameWorld.getPassability(loc);
    }

    @Override
    public MapLocation adjacentLocation(Direction dir) {
        return getLocation().add(dir);
    }

    // ***********************************
    // ****** READINESS METHODS **********
    // ***********************************

    private void assertIsReady() throws GameActionException {
        if (getCooldownTurns() >= 1)
            throw new GameActionException(IS_NOT_READY,
                    "This robot's action cooldown has not expired.");
    }

    /**
     * Check if the robot is ready to perform an action. Returns true if
     * the current cooldown counter is strictly less than 1.
     *
     * @return true if the robot can do an action, false otherwise
     */
    @Override
    public boolean isReady() {
        try {
            assertIsReady();
            return true;
        } catch (GameActionException e) { return false; }
    }

    /**
     * Return the cooldown turn counter of the robot. If this is < 1, the robot
     * can perform an action; otherwise, it cannot.
     * The counter is decreased by 1 at the start of every
     * turn, and increased to varying degrees by different actions taken.
     *
     * @return the number of cooldown turns as a float
     */
    @Override
    public double getCooldownTurns() {
        return this.robot.getCooldownTurns();
    }

    // ***********************************
    // ****** MOVEMENT METHODS ***********
    // ***********************************

    private void assertCanMove(Direction dir) throws GameActionException {
        assertNotNull(dir);
        if (!getType().canMove())
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot move.");
        MapLocation loc = adjacentLocation(dir);
        if (!onTheMap(loc))
            throw new GameActionException(OUT_OF_RANGE,
                    "Can only move to locations on the map; " + loc + " is not on the map.");
        if (isLocationOccupied(loc))
            throw new GameActionException(CANT_MOVE_THERE,
                    "Cannot move to an occupied location; " + loc + " is occupied.");
        if (!isReady())
            throw new GameActionException(IS_NOT_READY,
                    "Robot is still cooling down! You need to wait before you can perform another action.");
    }

    @Override
    public boolean canMove(Direction dir) {
        try {
            assertCanMove(dir);
            return true;
        } catch (GameActionException e) { return false; }
    }

    @Override
    public void move(Direction dir) throws GameActionException {
        assertCanMove(dir);

        MapLocation center = adjacentLocation(dir);
        this.robot.addCooldownTurns();
        this.gameWorld.moveRobot(getLocation(), center);
        this.robot.setLocation(center);

        gameWorld.getMatchMaker().addMoved(getID(), getLocation());
    }

    // ***********************************
    // ****** BUILDING/SPAWNING **********
    // ***********************************

    private void assertCanBuildRobot(RobotType type, Direction dir, int influence) throws GameActionException {
        assertNotNull(type);
        assertNotNull(dir);
        if (!getType().canBuild(type))
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot build robots of type" + type + ".");
        if (influence <= 0)
            throw new GameActionException(CANT_DO_THAT,
                    "Cannot spend nonpositive amount of influence.");
        if (influence > getInfluence())
            throw new GameActionException(CANT_DO_THAT,
                    "Cannot spend more influence than you have.");
        MapLocation spawnLoc = adjacentLocation(dir);
        if (!onTheMap(spawnLoc))
            throw new GameActionException(OUT_OF_RANGE,
                    "Can only spawn to locations on the map; " + spawnLoc + " is not on the map.");
        if (isLocationOccupied(spawnLoc))
            throw new GameActionException(CANT_MOVE_THERE,
                    "Cannot spawn to an occupied location; " + spawnLoc + " is occupied.");
        if (!isReady())
            throw new GameActionException(IS_NOT_READY,
                    "Robot is still cooling down! You need to wait before you can perform another action.");
    }

    @Override
    public boolean canBuildRobot(RobotType type, Direction dir, int influence) {
        try {
            assertCanBuildRobot(type, dir, influence);
            return true;
        } catch (GameActionException e) { return false; }
    }

    @Override
    public void buildRobot(RobotType type, Direction dir, int influence) throws GameActionException {
        assertCanBuildRobot(type, dir, influence);

        this.robot.addCooldownTurns();
        this.robot.addInfluenceAndConviction(-influence);

        int robotID = gameWorld.spawnRobot(this.robot, type, adjacentLocation(dir), getTeam(), influence);

        // set cooldown turns here, because not all new robots have cooldown (eg. switching teams)
        InternalRobot newBot = getRobotByID(robotID);
        newBot.setCooldownTurns(type.initialCooldown);

        gameWorld.getMatchMaker().addAction(getID(), Action.SPAWN_UNIT, robotID);
    }
    
    // ***********************************
    // ****** POLITICIAN METHODS ********* 
    // ***********************************

    private void assertCanEmpower(int radiusSquared) throws GameActionException {
        assertIsReady();
        if (!getType().canEmpower())
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot empower.");
        if (radiusSquared > getType().actionRadiusSquared)
            throw new GameActionException(CANT_DO_THAT,
                    "Robot's empower radius is smaller than radius specified");
    }

    @Override
    public boolean canEmpower(int radiusSquared) {
        try {
            assertCanEmpower(radiusSquared);
            return true;
        } catch (GameActionException e) { return false; } 
    }
    
    @Override
    public void empower(int radiusSquared) throws GameActionException {
        assertCanEmpower(radiusSquared);

        this.robot.addCooldownTurns(); // not needed but here for the sake of consistency
        this.robot.empower(radiusSquared);
        gameWorld.getMatchMaker().addAction(getID(), Action.EMPOWER, radiusSquared);

        // self-destruct
        gameWorld.destroyRobot(this.robot.getID());
    }


    // ***********************************
    // ****** MUCKRAKER METHODS ********** 
    // *********************************** 
    
    private void assertCanExpose(MapLocation loc) throws GameActionException {
        assertIsReady();
        if (!getType().canExpose())
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot expose.");
        if (!onTheMap(loc))
            throw new GameActionException(OUT_OF_RANGE,
                    "Location is not on the map.");
        if (!this.robot.canActLocation(loc))
            throw new GameActionException(CANT_DO_THAT,
                    "Location can't be exposed because it is out of range.");
        InternalRobot bot = gameWorld.getRobot(loc);
        if (bot == null)
            throw new GameActionException(CANT_DO_THAT,
                    "There is no robot at specified location.");
        if (!(bot.getType().canBeExposed()))
            throw new GameActionException(CANT_DO_THAT, 
                    "Robot at target location is not of a type that can be exposed.");
        if (bot.getTeam() == getTeam())
            throw new GameActionException(CANT_DO_THAT,
                    "Robot at target location is not on the enemy team.");
    }

    private void assertCanExpose(int id) throws GameActionException {
        assertIsReady();
        if (!getType().canExpose())
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot expose.");
        if (!canSenseRobot(id))
            throw new GameActionException(OUT_OF_RANGE,
                    "The targeted robot cannot be sensed.");
        InternalRobot bot = getRobotByID(id);
        if (!this.robot.canActLocation(bot.getLocation()))
            throw new GameActionException(OUT_OF_RANGE,
                    "Robot can't be exposed because it is out of range.");
        if (!(bot.getType().canBeExposed()))
            throw new GameActionException(CANT_DO_THAT, 
                    "Robot is not of a type that can be exposed.");
        if (bot.getTeam() == getTeam())
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is not on the enemy team.");
    }

    @Override
    public boolean canExpose(MapLocation loc) {
        try {
            assertCanExpose(loc);
            return true;
        } catch (GameActionException e) { return false; }  
    }

    @Override
    public boolean canExpose(int id) {
        try {
            assertCanExpose(id);
            return true;
        } catch (GameActionException e) { return false; }  
    }
    
    @Override
    public void expose(MapLocation loc) throws GameActionException {
        assertCanExpose(loc);

        this.robot.addCooldownTurns();
        InternalRobot bot = gameWorld.getRobot(loc);
        int exposedID = bot.getID();
        this.robot.expose(bot);
        gameWorld.getMatchMaker().addAction(getID(), Action.EXPOSE, exposedID);
    }

    @Override
    public void expose(int id) throws GameActionException {
        assertCanExpose(id);

        this.robot.addCooldownTurns();
        InternalRobot bot = getRobotByID(id);
        this.robot.expose(bot);
        gameWorld.getMatchMaker().addAction(getID(), Action.EXPOSE, id);
    }

    // ***********************************
    // *** ENLIGHTENMENT CENTER METHODS **
    // ***********************************

    private void assertCanBid(int influence) throws GameActionException {
        if (!getType().canBid()) {
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot bid.");
        } else if (influence <= 0) {
            throw new GameActionException(CANT_DO_THAT,
                    "Can only bid non-negative amounts of influence.");
        } else if (influence > getInfluence()) {
            throw new GameActionException(CANT_DO_THAT,
                    "Not possible to bid influence you don't have.");
        }
    }

    @Override
    public boolean canBid(int influence) {
        try {
            assertCanBid(influence);
            return true;
        } catch (GameActionException e) { return false; }  
    }

    @Override
    public void bid(int influence) throws GameActionException {
        assertCanBid(influence);

        this.robot.setBid(influence);
        gameWorld.getMatchMaker().addAction(getID(), Action.PLACE_BID, influence);
    }

    // *****************************
    // **** COMBAT UNIT METHODS **** 
    // *****************************

    private void assertCanAttack(MapLocation loc) throws GameActionException {
        assertIsActionReady();
        if (!getType().canAttack())
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot attack.");
        if (!this.robot.canActLocation(loc))
            throw new GameActionException(OUT_OF_RANGE,
                    "Robot can't be attacked because it is out of range.");
        InternalRobot bot = getRobot(loc);
        if (bot.getTeam() == getTeam())
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is not on the enemy team.");
    }

    @Override
    boolean canAttack(MapLocation loc){
        try {
            assertCanAttack(loc);
            return true;
        } catch (GameActionException e) { return false; }  
    }

    @Override
    void attack(MapLocation loc) throws GameActionException{
        assertCanAttack(loc);
        this.robot.attack(loc);
        InternalRobot bot = gameWorld.getRobot(loc);
        int attackedID = bot.getID();
        gameWorld.getMatchMaker().addAction(getID(), Action.ATTACK, attackedID);
    }

    // *****************************
    // ****** ARCHON METHODS ****** 
    // *****************************

    private void assertCanHealDroid(MapLocation loc) throws GameActionException {
        assertIsActionReady();
        if (!getType().canHealDroid()) {
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot heal droids.");
        }else if (!this.robot.canActLocation(loc)){
            throw new GameActionException(OUT_OF_RANGE,
                    "This robot can't be healed belocation can't be min because it is out of range.");
        }
        InternalRobot bot = gameWorld.getRobot(loc);
        if (!(bot.getType().canBeHealed())){
            throw new GameActionException(CANT_DO_THAT, 
                    "Robot is not of a type that can be healed.");
        }
        if (bot.getTeam() != getTeam()){
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is not on your team so can't be healed.");
        }
    }

    @Override
    boolean canHealDroid(MapLocation loc){
        try {
            assertCanHealDroid(loc);
            return true;
        } catch (GameActionException e) { return false; }  
    }

    @Override
    void healDroid(MapLocation loc) throws GameActionException{
        assertCanHealDroid(loc);
        this.robot.healDroid(loc);
        InternalRobot bot = gameWorld.getRobot(loc);
        int healedID = bot.getID();
        gameWorld.getMatchMaker().addAction(getID(), Action.HEAL_DROID, healedID);
    }

    
    // ***********************
    // **** MINER METHODS **** 
    // ***********************

    private void assertCanMineLead(MapLocation loc) throws GameActionException {
        assertIsActionReady();
        if (!getType().canMine()) {
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot mine.");
        }else if (!this.robot.canActLocation(loc)){
            throw new GameActionException(OUT_OF_RANGE,
                    "Robot can't be healed because it is out of range.");
        }
        int leadAmount = gameWorld.getLeadCount(loc);
        if (leadAmount < 0){
            throw new GameActionException(CANT_DO_THAT, 
                    "Lead amount must be positive to be mined.");
        }
    }

    @Override
    boolean canMineLead(MapLocation loc){
        try {
            assertCanMineLead(loc);
            return true;
        } catch (GameActionException e) { return false; }  
    }

    @Override
    void mineLead(MapLocation loc) throws GameActionException{
        assertCanMineLead(loc);
        this.robot.mineLead(loc);
        gameWorld.getMatchMaker().addAction(getID(), Action.MINE_LEAD, loc);
    }

    private void assertCanMineGold(MapLocation loc) throws GameActionException {
        assertIsActionReady();
        if (!getType().canMine()) {
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot mine.");
        }else if (!this.robot.canActLocation(loc)){
            throw new GameActionException(OUT_OF_RANGE,
                    "This location can't be mined because it is out of range.");
        }
        int goldAmount = gameWorld.getGoldCount(loc);
        if (goldAmount < 0){
            throw new GameActionException(CANT_DO_THAT, 
                    "Gold amount must be positive to be mined.");
        }
    }

    @Override
    boolean canMineGold(MapLocation loc){
        try {
            assertCanMineGold(loc);
            return true;
        } catch (GameActionException e) { return false; }  
    }

    @Override
    void mineGold(MapLocation loc) throws GameActionException{
        assertCanMineGold(loc);
        this.robot.mineGold(loc);
        gameWorld.getMatchMaker().addAction(getID(), Action.MINE_GOLD, loc);
    }

    // *************************
    // **** BUILDER METHODS **** 
    // *************************

    private void assertCanUpgrade(MapLocation loc) throws GameActionException {
        assertIsActionReady();
        if (!getType().canUpgrade()) {
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot upgrade buildings.");
        }else if (!this.robot.canActLocation(loc)){
            throw new GameActionException(OUT_OF_RANGE,
                    "Robot can't be upgraded because it is out of range.");
        }
        InternalRobot bot = gameWorld.getRobot(loc);
        if (!(bot.getType().canBeUpgraded())){
            throw new GameActionException(CANT_DO_THAT, 
                    "Robot is not of a type that can be upgraded.");
        }
        if (bot.getTeam() != getTeam()){
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is not on your team so can't be upgraded.");
        }
        if (getLead() < bot.getLeadUpgradeCost()){
            throw new GameActionException(CANT_DO_THAT,
                    "You don't have enough lead to upgrade this robot.");
        }
        if (getGold() < bot.getGoldUpgradeCost()){
            throw new GameActionException(CANT_DO_THAT,
                    "You don't have enough gold to upgrade this robot.");
        }
    }

    @Override
    boolean canUpgrade(MapLocation loc){
        try {
            assertCanUpgrade(loc);
            return true;
        } catch (GameActionException e) { return false; }  
    }

    @Override
    void upgrade(MapLocation loc) throws GameActionException{
        assertCanUpgrade(loc);
        this.robot.upgrade(loc);
        InternalRobot bot = gameWorld.getRobot(loc);
        int upgradedID = bot.getID();
        gameWorld.getMatchMaker().addAction(getID(), Action.UPGRADE, upgradedID);
    }

    private void assertCanRepairBuilding(MapLocation loc) throws GameActionException {
        assertIsActionReady();
        if (!getType().canRepairBuilding()) {
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot repair buildings.");
        }else if (!this.robot.canActLocation(loc)){
            throw new GameActionException(OUT_OF_RANGE,
                    "Robot can't be repaired because it is out of range.");
        }
        InternalRobot bot = gameWorld.getRobot(loc);
        if (!(bot.getType().canBeUpgraded())){
            throw new GameActionException(CANT_DO_THAT, 
                    "Robot is not of a type that can be repair.");
        }
        if (bot.getTeam() != getTeam()){
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is not on your team so can't be repaired.");
        }
    }

    @Override
    boolean canRepairBuilding(MapLocation loc){
        try {
            assertCanRepairBuilding(loc);
            return true;
        } catch (GameActionException e) { return false; }  
    }

    @Override
    void repairBuilding(MapLocation loc) throws GameActionException{
        assertCanRepairBuilding(loc);
        this.robot.repairBuilding(loc);
        InternalRobot bot = gameWorld.getRobot(loc);
        int repairedID = bot.getID();
        gameWorld.getMatchMaker().addAction(getID(), Action.REPAIRD, repairedID);
    }

    // *******************************
    // **** ALCHEMIST LAB METHODS **** 
    // *******************************

    private void assertCanConvert() throws GameActionException {
        assertIsActionReady();
        if (!getType().canConvert()) {
            throw new GameActionException(CANT_DO_THAT,
                    "Robot is of type " + getType() + " which cannot convert lead to gold.");
        } else if (LEAD_TO_GOLD_RATE > getLead()) {
            throw new GameActionException(CANT_DO_THAT,
                    "You don't have enough lead to be able to convert to gold.");
        }
    }

    @Override
    boolean canConvert(){
        try {
            assertCanConvert();
            return true;
        } catch (GameActionException e) { return false; }  
    }

    @Override
    void convert() throws GameActionException{
        assertCanConvert();
        this.robot.convert();
        gameWorld.getMatchMaker().addAction(getID(), Action.CONVERT);
    }

    // ***********************************
    // ****** COMMUNICATION METHODS ****** 
    // ***********************************

    //TODO: Communication needs to be fixed

    private void assertCanSetFlag(int flag) throws GameActionException {
        if (flag < GameConstants.MIN_FLAG_VALUE || flag > GameConstants.MAX_FLAG_VALUE) {
            throw new GameActionException(CANT_DO_THAT, "Flag value out of range");
        }
    }

    @Override
    public boolean canSetFlag(int flag) {
        try {
            assertCanSetFlag(flag);
            return true;
        } catch (GameActionException e) { return false; }
    }

    @Override
    public void setFlag(int flag) throws GameActionException {
        assertCanSetFlag(flag);
        this.robot.setFlag(flag);
        gameWorld.getMatchMaker().addAction(getID(), Action.SET_FLAG, flag);
    }

    private void assertCanGetFlag(int id) throws GameActionException {
        InternalRobot bot = getRobotByID(id);
        if (bot == null)
            throw new GameActionException(CANT_DO_THAT,
                    "Robot of given ID does not exist.");
        if (getType() != RobotType.ENLIGHTENMENT_CENTER &&
            bot.getType() != RobotType.ENLIGHTENMENT_CENTER &&
            !canSenseLocation(bot.getLocation()))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Robot at location is out of sensor range and not an Enlightenment Center.");
    }

    @Override
    public boolean canGetFlag(int id) {
        try {
            assertCanGetFlag(id);
            return true;
        } catch (GameActionException e) { return false; }
    }

    @Override
    public int getFlag(int id) throws GameActionException {
        assertCanGetFlag(id);

        return getRobotByID(id).getFlag();
    } 

    //TODO: move this back to public?

    // ***********************************
    // ****** OTHER ACTION METHODS *******
    // ***********************************
    /**
     * This used to be public, but is not public in 2021 because
     * slanderers should not be able to self-destruct.
     */
    private void disintegrate() {
        throw new RobotDeathException();
    }

    @Override
    public void resign() {
        gameWorld.getObjectInfo().eachRobot((robot) -> {
            if (robot.getTeam() == getTeam()) {
                gameWorld.destroyRobot(robot.getID());
            }
            return true;
        });
    }

    // ***********************************
    // ******** DEBUG METHODS ************
    // ***********************************

    @Override
    public void setIndicatorDot(MapLocation loc, int red, int green, int blue) {
        assertNotNull(loc);
        gameWorld.getMatchMaker().addIndicatorDot(getID(), loc, red, green, blue);
    }

    @Override
    public void setIndicatorLine(MapLocation startLoc, MapLocation endLoc, int red, int green, int blue) {
        assertNotNull(startLoc);
        assertNotNull(endLoc);
        gameWorld.getMatchMaker().addIndicatorLine(getID(), startLoc, endLoc, red, green, blue);
    }

}
