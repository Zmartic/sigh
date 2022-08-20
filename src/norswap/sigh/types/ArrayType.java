package norswap.sigh.types;

public final class ArrayType extends Type
{
    public final Type componentType;
    public final Type innerMostType;
    public final int dimension;

    public ArrayType (Type componentType) {
        this.componentType = componentType;
        if(componentType instanceof ArrayType) {
            ArrayType tmp = (ArrayType) componentType;
            this.innerMostType = tmp.innerMostType;
            this.dimension = tmp.dimension + 1;
        }
        else {
            this.innerMostType = componentType;
            this.dimension = 1;
        }
    }

    @Override public String name() {
        return componentType.toString() + "[]";
    }

    @Override public boolean equals (Object o) {
        return this == o || o instanceof ArrayType && componentType.equals(o);
    }

    @Override public int hashCode () {
        return componentType.hashCode();
    }
}
