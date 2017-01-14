package lumbercheeseplayer;
import battlecode.common.*;
import sun.reflect.generics.tree.Tree;

public strictfp class RobotPlayer {
    public static final int SQUADRON_MOVE_CODE = 99996;
    static final int COMMANDER_SWITCH_CODE = 99999;
    static final int TARGET_KILLED_CODE = 99997;
    static final int MAX_SQUADRON_MEMBERS = 2;
    static final int MAX_RAGE = 30;
    static int rageMeter;
    static RobotController rc;
    static MapLocation myLocation;
    static Direction startingEnemyDirection;
    static Direction wasGoing = null;
    static MapLocation[] startingEnemyPos = null;
    static MapLocation[] startingTeamPos = null;
    static int squadronNr;
    static Team enemyteam;
    static Direction directionToEnemy = null;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        enemyteam = rc.getTeam().opponent();
        if (startingTeamPos == null) {
            startingEnemyPos = rc.getInitialArchonLocations(rc.getTeam().opponent()).clone();
            startingTeamPos = rc.getInitialArchonLocations(rc.getTeam()).clone();
        }

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
        }
    }

    static boolean metcorner = false;

    static void runArchon() throws GameActionException {
        getMapSymmettry();
        MapLocation loc = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                myLocation = rc.getLocation();
                // Generate a random direction
                Direction dir = randomDirection();

                // Randomly attempt to build a gardener in this direction
                if (rc.canHireGardener(dir) && (rc.getRoundNum() <= 2 || rc.getTeamBullets() < RobotType.GARDENER.bulletCost)) {
                    rc.hireGardener(dir);
                }

                // Move randomly
                pathTo(dir);

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static void runGardener() throws GameActionException {
        int squadronMembersBuilt = 0;
        boolean squadronBuilder = false;

        if (rc.getRoundNum() <= 3){
            squadronBuilder = true;
        }
        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                myLocation = rc.getLocation();

                // Listen for home archon's location
                //int xPos = rc.readBroadcast(0);
                //int yPos = rc.readBroadcast(1);
                //MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Generate a random direction
                Direction dir = randomDirection();

                if (squadronMembersBuilt == MAX_SQUADRON_MEMBERS - 1){
                    squadronMembersBuilt = 0;
                }

                //if (squadronMembersBuilt == 0) {
                    if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady() && squadronBuilder) {
                        rc.buildRobot(RobotType.LUMBERJACK, dir);
                        squadronMembersBuilt += 1;
                    }
                //}
                if (squadronMembersBuilt == 0) {
                    // Move randomly
                   pathTo(randomDirection());
                }
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // If there are some...
                if (robots.length > 0) {
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(robots[0].location));
                    }
                }

                // Move randomly
                     pathTo(randomDirection());


                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        boolean isCommander = commanderOrNot();

        if (isCommander) {
            //Start new squadron and notify that there is a new one added
            squadronNr = rc.readBroadcast(0) + 1;
            rc.broadcast(0, squadronNr + 1);
            commanderMove();
        }else{
            //Join the last squadron
            squadronNr = rc.readBroadcast(0) - 1;
            int readMsg = rc.readBroadcast(squadronNr);
            rc.broadcast(squadronNr, readMsg + 1);
            squadronMemberControl();
        }
    }

    static void commanderMove() throws GameActionException{
        boolean isGoingSomewhere = false;
        int spawnCounter = 0;
        int timesBeenToEnemySpawn = 0;

        MapLocation someSpawn = new MapLocation(0,0);
        Direction toSomeSpawn;
        MapLocation[] mapLocations = mergeArrays(startingEnemyPos,startingTeamPos);
        // The code you want your robot to perform every round should be in this loop
        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                myLocation = rc.getLocation();
                if (rc.getHealth() > RobotType.LUMBERJACK.maxHealth * .1) {
                    // If there aren't enough members to form a squadron wait
                    if (rc.readBroadcast(squadronNr) >= MAX_SQUADRON_MEMBERS - 1) {
                        commanderAttack();

                        if (!rc.hasMoved() && !rc.hasAttacked()) {
                            if (isGoingSomewhere) {
                                toSomeSpawn = myLocation.directionTo(someSpawn);

                                pathTo(toSomeSpawn);
                                rc.broadcast(squadronNr, encodeCoordinates(Math.round(myLocation.x), Math.round(myLocation.y)));
                                rc.broadcast(squadronNr + 1, SQUADRON_MOVE_CODE);
                            } else if (rc.onTheMap(rc.getLocation().add(startingEnemyDirection, RobotType.LUMBERJACK.sensorRadius - 0.1f)) && timesBeenToEnemySpawn != startingEnemyPos.length) {
                                //System.out.println("Going to their spawn");
                                //if you're not close to the end of the map and you can move go towards the enemy spawn
                                startingEnemyDirection = myLocation.directionTo(startingEnemyPos[timesBeenToEnemySpawn]);
                                pathTo(startingEnemyDirection);
                                int encodedmessage = encodeCoordinates(Math.round(myLocation.x), Math.round(myLocation.y));
                                rc.broadcast(squadronNr + 1, SQUADRON_MOVE_CODE);
                                rc.broadcast(squadronNr, encodedmessage);
                            } else if (isStuckInCorner() && !isGoingSomewhere) {
                                if (spawnCounter == mapLocations.length) {
                                    spawnCounter = 0;
                                }
                                someSpawn = mapLocations[spawnCounter];
                                toSomeSpawn = myLocation.directionTo(someSpawn);
                                isGoingSomewhere = true;

                                pathTo(toSomeSpawn);
                                spawnCounter += 1;
                                rc.broadcast(squadronNr, encodeCoordinates(Math.round(myLocation.x), Math.round(myLocation.y)));
                                rc.broadcast(squadronNr + 1, SQUADRON_MOVE_CODE);
                            } else {
                                tryMove(randomDirection());
                                rc.broadcast(squadronNr, encodeCoordinates(Math.round(myLocation.x), Math.round(myLocation.y)));
                                rc.broadcast(squadronNr + 1, SQUADRON_MOVE_CODE);
                                //System.out.println("Walking randomly");
                            }
                        }

                        if (timesBeenToEnemySpawn < startingTeamPos.length) {
                            if (rc.canSenseLocation(startingEnemyPos[timesBeenToEnemySpawn])) {
                                timesBeenToEnemySpawn += 1;
                            }
                        }

                        if (rc.canSenseLocation(someSpawn)) {
                            isGoingSomewhere = false;
                        }
                    }
                }else{
                    rc.broadcast(squadronNr + 1,COMMANDER_SWITCH_CODE);
                    commanderAttack();
                }
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    static void squadronMemberControl() throws GameActionException{
        while (true){
            try {
                int broadcast = rc.readBroadcast(squadronNr);
                int broadcastedId = rc.readBroadcast(squadronNr + 1);
                if (broadcast > MAX_SQUADRON_MEMBERS - 1) {
                    switch (broadcastedId) {
                        case COMMANDER_SWITCH_CODE:
                            commanderMove();
                            break;
                        case TARGET_KILLED_CODE:
                            directionToEnemy = null;
                            break;
                        default:
                            squadronMemberMove(broadcast, broadcastedId);
                            break;
                    }
                }
                Clock.yield();
            }catch (Exception x){
                System.out.println(x);
            }
        }
    }

    static void squadronMemberMove(int broadcast,int broadcastedID) throws GameActionException{
        myLocation = rc.getLocation();
        int[] decodedBroadcast = decodeCoordinates(broadcast).clone();
        MapLocation broadcastedLocation = new MapLocation(decodedBroadcast[0], decodedBroadcast[1]);
        if (broadcastedID != SQUADRON_MOVE_CODE) {
            RobotInfo[] meleeRangeEnemies = rc.senseNearbyRobots(rc.getType().bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemyteam);
            RobotInfo[] sensorRangeEnemies = rc.senseNearbyRobots(rc.getType().sensorRadius, enemyteam);

            RobotInfo target = null;
            boolean melee = false;

            if (sensorRangeEnemies.length > 0) {
                if (meleeRangeEnemies.length > 0) {
                    for (RobotInfo robotInfo : meleeRangeEnemies) {
                        if (robotInfo.getID() == broadcastedID) {
                            target = robotInfo;
                            melee = true;
                            break;
                        }
                    }
                }

                if (target == null) {
                    for (RobotInfo robotInfo : sensorRangeEnemies) {
                        if (robotInfo.getID() == broadcastedID) {
                            target = robotInfo;
                            break;
                        }
                    }
                }

                if (target != null) {
                    if (directionToEnemy == null) {
                        directionToEnemy = myLocation.directionTo(target.getLocation());
                    }
                        MapLocation offsetLocation = target.location.add(directionToEnemy.rotateRightDegrees(90), target.getType().bodyRadius + rc.getType().bodyRadius);

                        if (melee) {
                            float stickyDistance = rc.getType().bodyRadius + target.getType().bodyRadius;
                            float distanceToEnemy = myLocation.distanceTo(target.location);
                            //avoidFriendlyFire(target.getLocation());
                            if (distanceToEnemy > stickyDistance && !rc.hasMoved()) {
                                tryMove(myLocation.directionTo(offsetLocation));
                                if (canKill(target)){
                                    rc.broadcast(squadronNr + 1,TARGET_KILLED_CODE);
                                }
                                RobotInfo[] alliedRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());

                                if (alliedRobots.length < 1) {
                                    rc.strike();
                                }
                            }else if (distanceToEnemy <= stickyDistance){
                                RobotInfo[] alliedRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());

                                if (alliedRobots.length < 1) {
                                    rc.strike();
                                }
                            }
                        } else {
                            tryMove(myLocation.directionTo(offsetLocation));
                        }
                    }
                } else {
                    pathTo(broadcastedLocation);
                }
            }else{
                pathTo(broadcastedLocation);
            }
        }




    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)x

        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    static boolean isStuckInCorner() throws GameActionException{
        int index = 0;
        int nrDirections = 0;
        Direction north = Direction.getNorth();
        while (index < 360) {
            Direction direction = north.rotateLeftDegrees(index);
            if (!rc.onTheMap(rc.getLocation().add(direction, RobotType.LUMBERJACK.sensorRadius - 0.1f))) {
                nrDirections += 1;
            }

            //System.out.println("indx " + index + "");
            index += 90;
        }
        return nrDirections >= 2;
    }

    static void getMapSymmettry() {
        MapLocation spawnA = rc.getInitialArchonLocations(Team.A)[0];
        MapLocation spawnB = rc.getInitialArchonLocations(Team.B)[0];
        if (spawnB.y == spawnA.y) {
            System.out.println("Vertical symmetry");
        } else if (spawnB.x == spawnA.x) {
            System.out.println("Horizontal symmetry");
        } else if (spawnA.x != spawnB.x && spawnA.y != spawnB.y) {
            System.out.println("Rotational symmetry");
        }
    }

     //Overloaded the pathTo method to accept a direction parameter
     static void pathTo(Direction direction) throws GameActionException{
         MapLocation location = myLocation.add(direction);
         pathTo(location);
     }

     //Overloaded the pathTo method to accept a direction parameter and a distance parameter
     static void pathTo(Direction direction, float distance) throws GameActionException{
         MapLocation location = myLocation.add(direction,distance);
         pathTo(location);
     }

     //Paths to the given MapLocation
     static void pathTo(MapLocation goalLocation) throws GameActionException {
        //Registers current map location as previous map location
        MapLocation prevLocation = myLocation;

        //Makes a direction from current position to goal location
        Direction goal = myLocation.directionTo(goalLocation);


        //If there is no previous moving direction make it the same as goal direction
        if (wasGoing == null){
            wasGoing = goal;
        }

        if (rageMeter >= MAX_RAGE && rc.getType() == RobotType.LUMBERJACK){
            goBallistic(goalLocation);
            return;
        }

        //If it can move to goal direction do so
        if (rc.canMove(goal)){
            rc.move(goal);
            myLocation = rc.getLocation();
        }else{
            rageMeter += 1;
            //If it can go at the same direction that it was going before continue
                if (rc.canMove(wasGoing)){
                    rc.move(wasGoing);

                    //Update location
                    myLocation = rc.getLocation();
                }else{
                    //If it cant check for a similar angle of previous direction if it can move to that do so
                    Direction closestDirection = checkBestAngle(goal,goalLocation);
                    if (closestDirection != null){
                        rc.move(closestDirection);
                        myLocation = rc.getLocation();
                    }

                }

        }
        //If it actually moved this turn update the previous direction with the direction that it moved
        if (prevLocation != rc.getLocation()) {
            wasGoing = prevLocation.directionTo(myLocation);
        }
    }
    static void goBallistic(MapLocation goalLocation) throws GameActionException{
        System.out.println("FUCK YOU TREES");
        if (rc.canMove(goalLocation)){
            rc.move(goalLocation);
            rageMeter -= 2;
        }else{
            TreeInfo[] trees = rc.senseNearbyTrees(rc.getType().bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS);
            if (trees.length > 0 && rc.canChop(trees[0].getLocation())) {
                rc.chop(trees[0].getLocation());
            }else if(trees.length == 0){
                rageMeter = 0;
            }
        }
    }
    /**
        It checks for the best angle to path to.
        @param goal The goal direction where we want to go
        @param goalLocation The goal location where we want to go
        @return Returns the angle that seems better
     **/
    static Direction checkBestAngle(Direction goal,MapLocation goalLocation) throws GameActionException{
        //The angle that is going to be returned
        Direction bestAngle;

        //Initial space between the previous direction that it was going and the goal direction
        float initDistance = wasGoing.degreesBetween(goal);
        int highPriority = 0;
        int medPriority = 0;

        //Checks If there is space aka the goal direction wasnt the same as the previous direction
        if (initDistance > 0) {
            //Measures the distance between the directions when you rotate the previous direction left by 1 degree
            //And checks if the space gets increased or decreased, this is used to see if the previous direction is going
            //more left or more right and prioritize an angle that goes similar to a similar direction with the previous one
            float secondDistance = wasGoing.rotateLeftDegrees(1).degreesBetween(goal);
            if(initDistance > secondDistance){
                highPriority = 1; //1 = left
                medPriority = 2;  //2 = right
            }else if (initDistance < secondDistance) {
                highPriority = 2;
                medPriority = 1;
            }
        }


        // Finds similar angles to the highPriority side left/right if it cant move to the high priority
        // check the medium priority, if it can't move to that either return null
        bestAngle = findSimilarAngles(wasGoing,highPriority);
        if (bestAngle == null) {
            bestAngle = findSimilarAngles(wasGoing, medPriority);
        }
        return bestAngle;
    }

    /**
     * Is used to the most similar angle to the direction given that the robot can go to according to the priority of the sides
     *
     * @param direction the direction
     * @param leftRight whether to check only for angles left or only for angles to the right
     * @return Direction if it finds one or null if the robot can't move
     */
    static Direction findSimilarAngles(Direction direction,int leftRight){
        int i = 0;
        double random = Math.random();

        // Iterates to every angle from 0 degrees to 100 degrees to the left/right by 10 degrees depending on the leftRight param
        // Returns a direction if the robot can move there
        while (i < 100){
            switch (leftRight){
                case 0:
                    if (random > .5){
                        direction = direction.rotateLeftDegrees(i);
                    }else{
                        direction = direction.rotateRightDegrees(i);
                    }
                case 1:
                    direction = direction.rotateLeftDegrees(i);
                    break;
                case 2:
                    direction = direction.rotateRightDegrees(i);
                    break;
            }
            if (rc.canMove(direction)){
                return direction;
            }
            i += 10;
        }
        return null;
    }

    static MapLocation[] mergeArrays(MapLocation[] locations1, MapLocation[] locations2){
        MapLocation[] mergedArray = new MapLocation[locations1.length * 2];
        int i = 0;
        for (MapLocation loc : locations1){
            mergedArray[i] = loc;
            i++;
        }
        for (MapLocation loc : locations2){
            mergedArray[i] = loc;
            i++;
        }
        return mergedArray;
    }

    static boolean commanderOrNot(){
        RobotInfo[] closeAllyRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.sensorRadius, rc.getTeam());
        boolean hascloseAlliedLumberjacks = false;

        for (RobotInfo info : closeAllyRobots){
            if (info.getType() == RobotType.LUMBERJACK && info.getHealth() == RobotType.LUMBERJACK.maxHealth){
                hascloseAlliedLumberjacks = true;
                break;
            }
        }

        if (!hascloseAlliedLumberjacks){
            return true;
        }
        return  false;
    }

    static int encodeCoordinates(int x, int y){
        int encodedMessage = (x * 1000) + y;
        return encodedMessage;
    }

    static int[] decodeCoordinates(int msg){
        int[] messageParts = new int[2];
        messageParts[1] = msg % 1000;
        msg = msg / 1000;
        messageParts[0] = msg;
        return messageParts;
    }

    static void commanderAttack() throws GameActionException { //or not
        RobotInfo[] meleeRangeEnemies = rc.senseNearbyRobots(rc.getType().bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemyteam);
        RobotInfo[] sensorRangeEnemies = rc.senseNearbyRobots(rc.getType().sensorRadius, enemyteam);
        RobotInfo[] alliedRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());

        RobotInfo target = null;
        boolean canAttackImportantTargets = false;
        boolean melee = false;

        // If there are enemies in sensor range
        if (sensorRangeEnemies.length > 0) {
            //If there are enemies in melee range
            if (meleeRangeEnemies.length > 0) {

                if (rc.getHealth() <= RobotType.LUMBERJACK.maxHealth * .25){
                    rc.broadcast(squadronNr + 1,COMMANDER_SWITCH_CODE);
                }

                for (RobotInfo robotInfo : meleeRangeEnemies) {
                    if (robotInfo.getType() == RobotType.ARCHON) {
                        target = robotInfo;
                        canAttackImportantTargets = true;
                    } else if (robotInfo.getType() == RobotType.GARDENER) {
                        target = robotInfo;
                        canAttackImportantTargets = true;
                    }
                }
                // If there arent any important targets in melee range pick the closest enemy as a target
                if (!canAttackImportantTargets) {
                    target = meleeRangeEnemies[0];
                }
                melee = true;
            }

            // If there arent any important targets in melee range search for targets in sensor radius
            if (!canAttackImportantTargets) {
                for (RobotInfo robotInfo : sensorRangeEnemies) {
                    if (robotInfo.getType() == RobotType.ARCHON) {
                        target = robotInfo;
                        melee = false;
                    } else if (robotInfo.getType() == RobotType.GARDENER) {
                        target = robotInfo;
                        melee = false;
                    }
                }
                // If there is no target in melee pick the closest on in sensor radius
                if (target == null) {
                    target = sensorRangeEnemies[0];
                }
            }

            if (directionToEnemy == null){
                directionToEnemy = myLocation.directionTo(target.getLocation());
            }

            MapLocation offsetLocation = target.location.add(directionToEnemy.rotateLeftDegrees(90),target.getType().bodyRadius + rc.getType().bodyRadius);
            if (melee){
                float stickyDistance = rc.getType().bodyRadius + target.getType().bodyRadius;
                float distanceToEnemy = myLocation.distanceTo(target.location);
                //avoidFriendlyFire(target.getLocation());
                if (distanceToEnemy > stickyDistance && !rc.hasMoved()){
                    tryMove(myLocation.directionTo(offsetLocation));
                    alliedRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());

                    if (alliedRobots.length < 2) {
                        rc.strike();
                    }
                }else if (distanceToEnemy <= stickyDistance && alliedRobots.length < 2){
                    rc.strike();
                }
            }else{
                    tryMove(myLocation.directionTo(offsetLocation));
            }
            if (rc.getHealth() > RobotType.LUMBERJACK.maxHealth * .1) {
                rc.broadcast(squadronNr, encodeCoordinates(Math.round(target.getLocation().x), Math.round(target.getLocation().y)));
                rc.broadcast(squadronNr + 1, target.getID());
            }else{
                rc.disintegrate();
            }
        }
    }

    static void avoidFriendlyFire(MapLocation target) throws GameActionException{
        RobotInfo[] alliedRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());

        if (alliedRobots.length == 0){
            rc.strike();
        }
        else if (alliedRobots.length == 1){
            Direction meToTarget = myLocation.directionTo(target);
            Direction allyToTarget = alliedRobots[0].getLocation().directionTo(target);

            float distanceToAlly = myLocation.distanceTo(alliedRobots[0].getLocation());
            float distanceInitial = allyToTarget.degreesBetween(meToTarget);
            float distanceSecond = allyToTarget.degreesBetween(meToTarget.rotateLeftDegrees(1));
            float bigRadius = rc.getType().bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS;
            float distanceToMove = RobotType.LUMBERJACK.bodyRadius + bigRadius - distanceToAlly;

            if (distanceInitial >= distanceSecond){
                if (rc.canMove(meToTarget.rotateRightDegrees(90),distanceToMove)) {
                    rc.move(meToTarget.rotateRightDegrees(90), distanceToMove);
                    rc.strike();
                }
            }else{
                if (rc.canMove(meToTarget.rotateRightDegrees(90),distanceToAlly)) {
                    rc.move(meToTarget.rotateRightDegrees(90), distanceToAlly);
                    rc.strike();
                }
            }
        }else {
            Clock.yield();
        }
    }

    static boolean canKill(RobotInfo target){
        if (myLocation.distanceTo(target.getLocation()) <= rc.getType().bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS && target.getHealth() <= rc.getType().attackPower){
            return true;
        }
        return false;
    }
}