import type { AxiosResponse } from 'axios';
import api from './axios';
import type { Address, AddressInput } from '../types';

// 내 주소록 목록 (기본 배송지 우선)
export const getMyAddresses = (): Promise<AxiosResponse<Address[]>> =>
  api.get('/api/v1/addresses');

// 배송지 추가 (첫 주소는 서버에서 자동 기본 지정)
export const addAddress = ({ receiver, phone, address }: AddressInput): Promise<AxiosResponse<Address>> =>
  api.post('/api/v1/addresses', { receiver, phone, address });

// 배송지 수정
export const updateAddress = (id: number, { receiver, phone, address }: AddressInput): Promise<AxiosResponse<Address>> =>
  api.put(`/api/v1/addresses/${id}`, { receiver, phone, address });

// 배송지 삭제
export const deleteAddress = (id: number): Promise<AxiosResponse<void>> =>
  api.delete(`/api/v1/addresses/${id}`);

// 기본 배송지 지정 (기존 기본 해제)
export const setDefaultAddress = (id: number): Promise<AxiosResponse<void>> =>
  api.patch(`/api/v1/addresses/${id}/default`);
