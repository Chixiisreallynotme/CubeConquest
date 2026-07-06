package fr.chixi.cubeconquest;

public enum Team {
    RED, BLUE;

    public Team opponent() {
        return this == RED ? BLUE : RED;
    }

    public String displayName() {
        return this == RED ? "Red" : "Blue";
    }
}
