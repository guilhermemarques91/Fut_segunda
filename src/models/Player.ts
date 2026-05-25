export interface PlayerAttributes {
  physical: number; // Ex: Resistência, Força
  technical: number; // Ex: Passe, Drible
  tactical: number; // Ex: Posicionamento, Visão de jogo
}

export class Player {
  public id: string;
  public name: string;
  public position: 'Atacante' | 'Zagueiro' | 'Meia';
  public attributes: PlayerAttributes;
  public isActive: boolean = true;

  constructor(id: string, name: string, position: 'Atacante' | 'Zagueiro' | 'Meia', attributes: PlayerAttributes) {
    this.id = id;
    this.name = name;
    this.position = position;
    this.attributes = attributes;
  }
}