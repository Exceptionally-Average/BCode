package lumbercheeseplayer;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Direction startingEnemyDirection;


    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        startingEnemyDirection = rc.getLocation().directionTo(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]);
        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        rc.getInitialArchonLocations(rc.getTeam());
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
        Direction direction = Direction.getNorth();
        getMapSymmettry();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                /*
                Direction outOfCorner = findDirectionOutOfCorner();
                if (!metcorner && outOfCorner != null){
                    direction = outOfCorner;
                    metcorner = true;
                }

                if (rc.canMove(direction)) {
                    tryMove(direction);
                }
                */

                // Generate a random direction
                Direction dir = randomDirection();

                // Randomly attempt to build a gardener in this direction
                if (rc.canHireGardener(dir) && (rc.getRoundNum() <= 2 || Math.random() < .01)) {
                    rc.hireGardener(dir);
                }

                // Move randomly
                tryMove(randomDirection());

                // Broadcast archon's location for other robots on the team to know
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);

                clearTemporaryBroadcasts();
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static void runGardener() throws GameActionException {
        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Listen for home archon's location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Generate a random direction
                Direction dir = randomDirection();

                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                }

                // Move randomly
                tryMove(randomDirection());
                clearTemporaryBroadcasts();
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
                MapLocation myLocation = rc.getLocation();

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
                tryMove(randomDirection());

                clearTemporaryBroadcasts();
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        int timesStuckInCorner = 0;
        boolean hasVisitedEnemySpawn = false;
        boolean isGoingSomewhere = false;
        MapLocation someSpawn = new MapLocation(0,0);
        Direction toSomeSpawn = startingEnemyDirection;

        // The code you want your robot to perform every round should be in this loop
        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                if (isStuckInCorner()) {
                    System.out.println("IM STUCK!");
                }

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                // See if there are any allied robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] alliedRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());

                if(robots.length > 0 && !rc.hasAttacked()) {
                    if (alliedRobots.length == 0) {
                        // Check if you can kill the enemy robot
                        if (RobotType.LUMBERJACK.attackPower >= robots[0].getHealth() && robots[0].getID() == rc.readBroadcast(2));{
                            clearTemporaryBroadcasts();
                        }

                        // Use strike() to hit all nearby robots!
                        rc.strike();
                    }else {
                        Direction oppositeDirection = rc.getLocation().directionTo(alliedRobots[0].location).opposite();
                        if (rc.canMove(oppositeDirection,GameConstants.LUMBERJACK_STRIKE_RADIUS)){
                            rc.move(oppositeDirection,GameConstants.LUMBERJACK_STRIKE_RADIUS);
                        }else{
                            rc.strike();
                        }
                    }
                }else if (rc.senseNearbyRobots(-1, enemy).length > 0){
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1, enemy);

                    // If there is a robot, move towards it
                    MapLocation myLocation = rc.getLocation();
                    MapLocation enemyLocation = robots[0].getLocation();
                    Direction toEnemy = myLocation.directionTo(enemyLocation);
                    rc.broadcast(2, robots[0].getID());
                    rc.broadcast(3, (int) enemyLocation.x);
                    rc.broadcast(4, (int) enemyLocation.y);
                    rc.getRobotCount();
                    tryMove(toEnemy);
                    //System.out.println("GOING TO FUCK SHIT UP");
                }else if (rc.readBroadcast(3) != 0 && rc.readBroadcast(4) != 0 && !rc.hasMoved()) {
                    MapLocation myLocation = rc.getLocation();
                    int EnemyxPos = rc.readBroadcast(3);
                    int EnemyyPos = rc.readBroadcast(4);
                    MapLocation enemyLocation = new MapLocation(EnemyxPos, EnemyyPos);
                    Direction toEnemy = myLocation.directionTo(enemyLocation);
                    tryMove(toEnemy);
                    //System.out.println("Going to the broadcast");
                }else if (isGoingSomewhere){
                    toSomeSpawn = rc.getLocation().directionTo(someSpawn);
                    tryMove(toSomeSpawn);
                }
                else if(rc.onTheMap(rc.getLocation().add(startingEnemyDirection,RobotType.LUMBERJACK.sensorRadius - 0.1f)) && !hasVisitedEnemySpawn) {
                    //System.out.println("Going to their spawn");
                    //if you're not close to the end of the map and you can move go towards the enemy spawn
                    tryMove(startingEnemyDirection);
                }else if (isStuckInCorner()) {
                    timesStuckInCorner += 1;
                    if (timesStuckInCorner % 2 == 0){
                        someSpawn = rc.getInitialArchonLocations(enemy)[0];
                        toSomeSpawn = rc.getLocation().directionTo(someSpawn);
                    }else {
                        someSpawn = rc.getInitialArchonLocations(rc.getTeam())[0];
                        toSomeSpawn = rc.getLocation().directionTo(someSpawn);
                    }
                    isGoingSomewhere = true;
                    tryMove(toSomeSpawn);
                }else{
                        tryMove(randomDirection());
                        //System.out.println("Walking randomly");
                }

                if (!hasVisitedEnemySpawn) {
                    if (rc.canSenseLocation(rc.getInitialArchonLocations(rc.getTeam().opponent())[0])) {
                        hasVisitedEnemySpawn = true;
                    }
                }

                if (rc.canSenseLocation(someSpawn)) {
                    isGoingSomewhere = false;
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
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
        MapLocation myLocation = rc.getLocation();

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

    //clears the broadcasts that are updated each turn
    static void clearTemporaryBroadcasts() throws GameActionException{
            rc.broadcast(2, 0);
            rc.broadcast(3, 0);
            rc.broadcast(4, 0);
    }

    static Direction findDirectionOutOfCorner() throws GameActionException {
        Direction[] offTheMapDirections = new Direction[2];
        int index = 0;
        int nrDirections = 0;
        int signednr = 1;

        Direction north = Direction.getNorth();
        while (index < 360) {
            Direction direction = north.rotateLeftDegrees(index);
            if (!rc.onTheMap(rc.getLocation().add(direction, RobotType.LUMBERJACK.sensorRadius - 0.1f))) {
                offTheMapDirections[nrDirections] = direction.opposite();
                 nrDirections += 1;
                if (index >= 180 && signednr > 0) {
                    signednr = signednr * -1;
                }
            }

            //System.out.println("indx " + index + "");
            index += 90;
        }

        if (nrDirections == 2) {
            Direction direction = startingEnemyDirection;
            if (offTheMapDirections[0].equals(Direction.getEast()) || offTheMapDirections[1].equals(Direction.getEast())) {
                direction = Direction.getEast().rotateRightDegrees(signednr * 45);
                System.out.println("EAST!");
            } else if (offTheMapDirections[0].equals(Direction.getWest()) || offTheMapDirections[1].equals(Direction.getWest())) {
                direction = Direction.getWest().rotateLeftDegrees(signednr * 45);
                System.out.println("WEST!");
            }
            return direction;
        }else if (nrDirections == 1) {
            return randomDirection();
        }else{
            return null;
        }
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

    static void getMapSymmettry(){
        MapLocation spawnA = rc.getInitialArchonLocations(Team.A)[0];
        MapLocation spawnB = rc.getInitialArchonLocations(Team.B)[0];
        if (spawnB.y == spawnA.y){
            System.out.println("Vertical symmetry");
        }else if (spawnB.x == spawnA.x){
            System.out.println("Horizontal symmetry");
        }else if (spawnA.x != spawnB.x && spawnA.y != spawnB.y){
            System.out.println("Rotational symmetry");
        }
    }
}