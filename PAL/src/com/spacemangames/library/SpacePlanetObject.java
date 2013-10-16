package com.spacemangames.library;

import com.spacemangames.framework.IMoveProperties;

public class SpacePlanetObject extends SpaceObject {
    private boolean mDOI;    // DOI = Death On Impact
    public float    mGravity;

    public SpacePlanetObject(String aBitmap, boolean lazyLoading, int aX, int aY, float aGrav, int aCollisionSize, boolean aDOI,
            IMoveProperties moveProperties) {
        super(aBitmap, lazyLoading, TYPE_PLANET, aX, aY, aCollisionSize, moveProperties);

        mGravity = aGrav;
        mDOI = aDOI;
    }

    @Override
    public float gravity() {
        return mGravity;
    }

    @Override
    public void setGravity(float aGravity) {
        mGravity = aGravity;
    }

    @Override
    public boolean deathOnImpact() {
        return mDOI;
    }

    @Override
    public void setDeathOnImpact(boolean aDie) {
        mDOI = aDie;
    }
}
