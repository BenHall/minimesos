package com.containersol.minimesos.config

class ResourceDef extends Block {

    String role
    def value

    public ResourceDef() {

    }

    public ResourceDef(String role, def value) {
        this.role = role
        this.value = value
    }

}