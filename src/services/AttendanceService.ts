
import { Player } from '../models/Player';
import { AttendanceRecord } from '../models/Match';

/**
 * Serviço responsável por gerenciar a presença dos jogadores em eventos.
 */
export class AttendanceService {
  /**
   * Simula o recebimento de confirmações de presença via WhatsApp ou API externa.
   * @param matchId O ID da partida para registrar a presença.
   * @param playerIds Lista de IDs dos jogadores que confirmaram presença.
   * @returns Um objeto AttendanceRecord atualizado com os dados de presença.
   */
  public async recordAttendance(matchId: string, playerIds: string[]): Promise<AttendanceRecord> {
    // Em um ambiente real, aqui haveria uma chamada API/Webhook para processar a lista de IDs.
    console.log(`[SIMULAÇÃO] Recebendo ${playerIds.length} confirmações de presença para o Match ID: ${matchId}`);

    const record: AttendanceRecord = {
      matchId: matchId,
      date: new Date(), // Usar a data atual ou passar como parâmetro
      presentPlayerIds: playerIds,
      adminAddedAttendees: [], // Inicialmente vazio
    };

    // Lógica de validação e persistência seria adicionada aqui.
    return record;
  }

  /**
   * Adiciona manualmente participantes à lista de presença (usado por administradores).
   * @param matchId O ID da partida.
   * @param adminPlayerIds IDs dos jogadores adicionados manualmente.
   * @returns Um objeto AttendanceRecord atualizado.
   */
  public async addAdminAttendees(matchId: string, adminPlayerIds: string[]): Promise<AttendanceRecord> {
    // Buscar o registro de presença existente (simulação)
    const existingRecord: AttendanceRecord = {
      matchId: matchId,
      date: new Date(),
      presentPlayerIds: [], // Deve ser carregado do DB
      adminAddedAttendees: [],
    };

    console.log(`[SIMULAÇÃO] Adicionando ${adminPlayerIds.length} participantes manualmente para o Match ID: ${matchId}`);

    const updatedRecord: AttendanceRecord = {
      ...existingRecord,
      adminAddedAttendees: [...existingRecord.adminAddedAttendees, ...adminPlayerIds],
    };

    return updatedRecord;
  }
}
