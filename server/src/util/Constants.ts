class Constants {
    static PLAYER_COLORS: number[] = [
        0x4cb050FF,
        0xe6194bFF, 0x3cb44bFF, 0xffe119FF, 0x4363d8FF,
        0xf58231FF, 0x911eb4FF, 0x46f0f0FF, 0xf032e6FF,
        0xbcf60cFF, 0xfabebeFF, 0x008080FF, 0xe6beffFF,
        0x9a6324FF, 0xfffac8FF, 0x800000FF, 0xaaffc3FF,
    ];

    static FRUIT_COLORS: number[] = [
        0xFF0000FF,
        0x00FF00FF,
        0x0000FFFF
    ];

    static WORLD_UPDATE_INTERVAL = 16; // ms
    static INIT_FRUITS = 50;
    static FRUIT_RADIUS = 10;
    static PLAYER_MIN_SPEED = 40;
    static PLAYER_INIT_SPEED = 120;
    static PLAYER_INIT_RADIUS = 40;
}

export default Constants;