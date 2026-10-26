package t.mma.hooks.items.homeextension

/** Live favorites order; absent current starts at first/last, shuffle excludes current. */
internal fun favoriteStepIndex(size:Int, current:Int, direction:Int, shuffle:Boolean):Int {
    require(size>0)
    if(size==1)return 0
    if(shuffle){val candidates=(0 until size).filter{it!=current};return candidates.random()}
    if(current !in 0 until size)return if(direction<0)size-1 else 0
    return (current+if(direction<0)-1 else 1).mod(size)
}
