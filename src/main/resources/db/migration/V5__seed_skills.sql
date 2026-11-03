-- V5: população inicial do catálogo de habilidades
INSERT INTO skills
(name, effect_category, description, initial_skill)
VALUES

    (
        'BOMBA',
        'REMOVAL',
        'Remove uma peça do oponente do tabuleiro, deixando a casa vazia novamente.',
        true
    ),

    (
        'ESCUDO',
        'PROTECTION',
        'Bloqueia uma casa vazia do tabuleiro, impedindo que o oponente ocupe esse espaço por 2 rodadas.',
        true
    ),

    (
        'TORNADO',
        'RELOCATION',
        'Mover uma peça do oponente para uma casa adjacente que esteja vazia.',
        true
    ),

    (
        'BURACO NEGRO',
        'RANDOM_RELOCATION',
        'Suga uma peça do oponente e a projeta para uma casa vazia aleatória do tabuleiro.',
        true
    ),

    (
        'AMPULHETA',
        'TIME_CONTROL',
        'Reduz o tempo de jogada do oponente na rodada seguinte de 10 segundos para 5 segundos.',
        true
    ),

    (
        'CONGELAR',
        'DISABLE',
        'Congela uma peça do oponente por 2 rodadas. A peça permanece no tabuleiro, mas é ignorada no cálculo de vitória.',
        true
    ),

    (
        'ESPELHO',
        'DEFENSE',
        'Anula a próxima habilidade usada contra você.',
        false
    ),

    (
        'RADAR',
        'INFORMATION',
        'Revela uma casa vazia que participa de pelo menos uma linha potencial de vitória ou bloqueio. A habilidade fornece uma informação tática, mas não garante a melhor jogada.',
        false
    ),

    (
        'TROCA RAPIDA',
        'POSITION',
        'Move uma peça própria para uma casa adjacente vazia.',
        false
    ),

    (
        'REFORCO',
        'DEFENSE',
        'Protege uma peça própria contra remoção ou movimentação por 2 rodadas.',
        false
    );
