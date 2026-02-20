package com.example.buddychat.network.model;

// ================================================================================
// Java class mapping of the backend DB "Profile" object
// ================================================================================
public class Profile {
    // Main fields we care about
    public int     id;
    public Account account;

    // Other attributes
    public String   zipcode;
    public String   birthDate;
    public String   locationStatus;
    public Settings settings;
    public Goal     goal;

    // --------------------------------------------------------------------------------
    // Shortcuts to "User" values
    // --------------------------------------------------------------------------------
    public Integer userId   () { return account != null && account.user != null ? account.user.id         : null; }
    public String  username () { return account != null && account.user != null ? account.user.username   : null; }
    public String  firstName() { return account != null && account.user != null ? account.user.first_name : null; }
    public String  lastName () { return account != null && account.user != null ? account.user.last_name  : null; }

    // ================================================================================
    // Class definitions for "Profile" attributes
    // ================================================================================
    public static class Account {
        public int    id;
        public User   user;
        public String role;
    }

    public static class User {
        public int     id;
        public String  username;
        public String  first_name;
        public String  last_name;
        public boolean is_staff;
    }

    // --------------------------------------------------------------------------------
    // These are currently parsed but unused by the robot
    // --------------------------------------------------------------------------------
    public static class Settings {
        public boolean patientViewOverall;
        public boolean patientCanSchedule;
        public String  taskType;
        public String  taskSubtype;
        public String  modelChoice;
    }

    public static class Goal {
        public int     id;
        public int     target;
        public boolean auto_renew;
        public String  period;
        public String  start_date;
        public int     start_dow;
        public int     current;
        public int     remaining;
    }
}
